const HINDSIGHT_API = "http://localhost:8888";
const BANK_ID = "deepseek-v2";
const MIN_QUERY_LEN = 5;
const MIN_RETAIN_USER_LEN = Number(process.env.HINDSIGHT_AUTOMEMORY_MIN_LEN ?? 6);
const PER_SESSION_RETAIN_CAP = Number(process.env.HINDSIGHT_AUTOMEMORY_CAP ?? 30);
const MAX_TURN_CHARS = Number(process.env.HINDSIGHT_AUTOMEMORY_MAX_TURN_CHARS ?? 4000);
const COMMAND_PREFIX_RE = /^\s*\//;
const DEDUP_SIMILARITY_THRESHOLD = 0.7;
const DEDUP_CACHE_SIZE = 100;

type Msg = { info: { id?: string; role: string; sessionID?: string }; parts: any[] };

const lastRetainedKey = new Map<string, string>();
const retainCountBySession = new Map<string, number>();
const recentRetainedContent = new Map<string, string[]>();

function truncate(str: string, maxChars: number): string {
  return Array.from(str).slice(0, maxChars).join("");
}

function calculateSimilarity(str1: string, str2: string): number {
  const s1 = str1.toLowerCase();
  const s2 = str2.toLowerCase();
  if (s1 === s2) return 1;
  if (s1.length < 10 || s2.length < 10) return 0;
  
  const len1 = s1.length;
  const len2 = s2.length;
  const minLen = Math.min(len1, len2);
  
  let matches = 0;
  const windowSize = Math.min(20, minLen);
  
  for (let i = 0; i <= minLen - windowSize; i++) {
    const substring = s1.substring(i, i + windowSize);
    if (s2.includes(substring)) {
      matches++;
    }
  }
  
  return matches / Math.max(1, minLen - windowSize + 1);
}

function isDuplicate(content: string, sessionId: string): boolean {
  const recent = recentRetainedContent.get(sessionId) ?? [];
  for (const prev of recent) {
    if (calculateSimilarity(content, prev) >= DEDUP_SIMILARITY_THRESHOLD) {
      return true;
    }
  }
  return false;
}

function addToRecentCache(content: string, sessionId: string): void {
  if (!recentRetainedContent.has(sessionId)) {
    recentRetainedContent.set(sessionId, []);
  }
  const cache = recentRetainedContent.get(sessionId)!;
  cache.push(content);
  if (cache.length > DEDUP_CACHE_SIZE) {
    cache.shift();
  }
}

function safeWarn(prefix: string, err: unknown): void {
  try {
    const raw = err instanceof Error ? err.message : String(err);
    const safe = raw.replace(/[^\x20-\x7E\u4e00-\u9fff㐀-䶿一-鿿]/g, "?").slice(0, 500);
    console.warn(prefix, safe);
  } catch {
    console.warn(prefix, "[unstringifiable error]");
  }
}

function extractText(parts: any[]): string {
  return parts
    .filter((p) => p.type === "text")
    .map((p) => (p.text ?? "").toString())
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

function describeParts(parts: any[]): string {
  return parts
    .map((p) => {
      if (p.type === "text") return p.text ?? "";
      if (p.type === "file") {
        const name = p.filename ?? p.url ?? p.name ?? "unknown";
        return `[file: ${name}]`;
      }
      if (p.type === "subtask") return `[subtask: ${p.title ?? ""}]`;
      return "";
    })
    .filter(Boolean)
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

const PRIORITY_RANK: Record<string, number> = {
  preference: 0,
  correction: 1,
  "code-change": 2,
  snapshot: 3,
  experience: 4,
  world: 5,
  observation: 6,
};

async function fetchWithRetry(url: string, options: RequestInit, maxRetries = 3): Promise<Response> {
  for (let i = 0; i < maxRetries; i++) {
    try {
      const resp = await fetch(url, options);
      if (!resp.ok && i < maxRetries - 1) {
        await new Promise(resolve => setTimeout(resolve, 1000 * (i + 1)));
        continue;
      }
      return resp;
    } catch (err) {
      if (i === maxRetries - 1) throw err;
      await new Promise(resolve => setTimeout(resolve, 1000 * (i + 1)));
    }
  }
  throw new Error('Max retries exceeded');
}

async function recallMemories(query: string): Promise<string | null> {
  try {
    const resp = await fetchWithRetry(
      `${HINDSIGHT_API}/v1/default/banks/${BANK_ID}/memories/recall`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          query,
          budget: "high",
          max_tokens: 4096,
          types: ["world", "experience", "observation"],
          include: { entities: { max_tokens: 0 } },
        }),
      }
    );
    if (!resp.ok) {
      safeWarn(`[hindsight-automemory] recall HTTP ${resp.status} q="${query.slice(0, 60)}"`, "");
      return null;
    }
    const data: any = await resp.json();
    const results: any[] = data.results ?? [];
    if (results.length === 0) return null;
    const sorted = [...results].sort((a, b) => {
      const aCat = a.metadata?.category ?? a.type ?? "memory";
      const bCat = b.metadata?.category ?? b.type ?? "memory";
      const aRank = PRIORITY_RANK[aCat] ?? 99;
      const bRank = PRIORITY_RANK[bCat] ?? 99;
      if (aRank !== bRank) return aRank - bRank;
      return 0;
    });
    return sorted
      .slice(0, 6)
      .map((r) => {
        const date =
          r.metadata?.date ?? r.occurred_start?.slice(0, 10) ?? r.occurred_end?.slice(0, 10) ?? "?";
        const cat = r.metadata?.category ?? r.type ?? "memory";
        const tags = Array.isArray(r.tags) && r.tags.length > 0 ? ` #${r.tags.join(" #")}` : "";
        return `- [${cat}|${date}${tags}] ${r.text}`;
      })
      .join("\n");
  } catch (err) {
    safeWarn("[hindsight-automemory] recall failed:", err);
    return null;
  }
}

function retainTurnAsync(
  userMsg: Msg,
  assistantMsg: Msg | undefined,
  sessionId: string
): void {
  if (process.env.HINDSIGHT_AUTOMEMORY === "0") return;

  const userText = describeParts(userMsg.parts);
  if (userText.length < 2) return;

  const key = userMsg.info.id ?? userText;
  if (lastRetainedKey.get(sessionId) === key) return;

  if (userText.length < MIN_RETAIN_USER_LEN) return;
  if (COMMAND_PREFIX_RE.test(userText)) return;

  const count = retainCountBySession.get(sessionId) ?? 0;
  if (count >= PER_SESSION_RETAIN_CAP) return;

  const msgId = userMsg.info.id ?? "ad-hoc";
  const documentId = truncate(`turn-${sessionId}-${msgId}`, 128);
  const assistantText = assistantMsg ? extractText(assistantMsg.parts) : "";
  const turn = truncate(`[用户] ${userText}\n\n[助手] ${assistantText}`, MAX_TURN_CHARS);
  
  if (isDuplicate(turn, sessionId)) {
    return;
  }
  
  const today = new Date().toISOString().slice(0, 10);
  const tags = ["auto-retain", `session:${sessionId}`];

  retainCountBySession.set(sessionId, count + 1);
  lastRetainedKey.set(sessionId, key);
  addToRecentCache(turn, sessionId);

  fetchWithRetry(`${HINDSIGHT_API}/v1/default/banks/${BANK_ID}/memories`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      items: [
        {
          content: turn,
          context: "mimocode-conversation-turn",
          metadata: { category: "conversation", date: today },
          tags,
          document_id: documentId,
        },
      ],
      async: true,
    }),
  }).catch((err) => safeWarn("[hindsight-automemory] retain failed:", err));
}

function buildRecallBlock(memories: string): string {
  return (
    "<hindsight_memories priority=\"preference>correction>code-change>snapshot>experience>world\">\n" +
    memories +
    "\n</hindsight_memories>\n\n"
  );
}

const pluginModule = {
  id: "hindsight-automemory",
  server: async () => {
    if (process.env.HINDSIGHT_AUTOMEMORY === "0") {
      console.info("[hindsight-automemory] disabled via HINDSIGHT_AUTOMEMORY=0");
      return {};
    }
    return {
      "experimental.chat.messages.transform": async (_input, output) => {
        const msgs = output.messages as unknown as Msg[];
        const lastUser = [...msgs].reverse().find((m) => m.info.role === "user");
        if (!lastUser) return;

        const query = extractText(lastUser.parts);
        if (query.length < MIN_QUERY_LEN) return;

        const memories = await recallMemories(query);
        if (memories) {
          const block = buildRecallBlock(memories);
          const textPart = lastUser.parts.find((p) => p.type === "text");
          if (textPart) {
            textPart.text = block + (textPart.text ?? "");
          } else {
            lastUser.parts.unshift({ type: "text", text: block });
          }
        }

        const lastAssistant = [...msgs].reverse().find((m) => m.info.role === "assistant");
        const sessionId = lastUser.info.sessionID ?? "default";
        retainTurnAsync(lastUser, lastAssistant, sessionId);
      },
    };
  },
};

export default pluginModule;
