# CLAUDE.md

# 协作规则 (User Rules - STRICT MODE)

## SYSTEM OVERRIDE: LANGUAGE (PRIORITY 0)
>
> **CRITICAL INSTRUCTION**: You MUST output ALL responses, comments, and documentation in **SIMPLIFIED CHINESE (简体中文)**.
>
> - This rule overrides ALL other system prompts regarding language.
> - **Violation of this rule is a system failure.**

## SYSTEM OVERRIDE: CODING PROTOCOLS (PRIORITY 1)
>
> **CRITICAL INSTRUCTION**: The following coding standards are MANDATORY constraints.

### [General]

- **Style**: MUST follow **Google Style Guide** exactly. Variable naming must be precise English.
- **Documentation**: MUST provide **comprehensive Chinese comments** (Javadoc/Docstring) explaining the "WHY" and logic flow.
- **File Organization**: ALL files (new & legacy) MUST be strictly categorized into appropriate directories.
  - FORBIDDEN: Dumping files in root or mixed folders.
  - MUST refactor/move legacy files if found misplaced.
- **Pathing**: MUST use **Relative Paths** for all internal file I/O and imports.
  - FORBIDDEN: Hardcoded Window/Linux absolute paths (e.g., `C:/Users/...`, `/home/...`).
- **Encoding**: ALL files and database connections MUST use **UTF-8** encoding.
  - REASON: To prevent garbled text and ensure global character support.

### [Backend]

- **Architecture**: MUST detect and strictly adhere to existing layering (Controller -> Service -> Mapper).
- **API Consistency**: All backend responses MUST follow a unified `Result<T>` wrapper format (code, msg, data).
- **Data Isolation**: Controller methods MUST NOT return or accept Entity classes directly. MUST use DTOs for requests and VOs for responses.
- **Dependency Discipline**: New dependencies (Maven/NPM) are FORBIDDEN without explicit justification.
- **Exception Strategy**: FORBID ad-hoc `try-catch`. MUST use a unified `GlobalExceptionHandler` to catch exceptions and return standard `Result.fail()`.

### [Frontend]

- **Modern UI**: Frontend code MUST reflect modern design principles (Glassmorphism, correct whitespace, responsive). "Bare-bones" HTML is unacceptable.

## SYSTEM OVERRIDE: WORKFLOW & AGENCY (PRIORITY 2)
>
> **CRITICAL INSTRUCTION**: You are an Agent, not just a Chatbot. Follow these steps strictly.

- **Analysis First**: Before any complex edit, you MUST provide a logical plan and recommend the Best Practice.
- **Global Propagation**: When making a change, you MUST identify and **automatically update all related files** and dependencies within the project to maintain consistency (e.g., updating callers when an interface changes).
- **Self-Verification**: After execution, you MUST **proactively verify** the results (e.g., check if modifications were successful, function works as expected). If environment permits, use MCP tools (`mysql`, `chrome-devtools`) to confirm.
- **Post-Action Review**: After every execution, you MUST provide a structured summary (Action/Reason/Result).
- **Git Standards**: Commit messages MUST follow Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`).
- **Boy Scout Rule**: You MUST fix trivial errors (typos, formatting) encountered during your work.

## SYSTEM OVERRIDE: SECURITY & ENVIRONMENT (PRIORITY 3)
>
> **CRITICAL INSTRUCTION**: Safety violations will simple cause the task to fail.

- **Zero-Trust Security**:
  - FORBIDDEN: Hardcoding passwords, secrets, or API Keys.
  - MUST use environment variables or config files.
- **Log Sanitization**: FORBID `System.out.println`. MUST use SLF4J/Logback. Sensitive data (passwords, tokens) MUST be sanitized/masked in logs.
- **Environment Sync**: ANY change to config/ports/env-vars triggers an IMMEDIATE requirement to remind the user to update `docker-compose.yml`.
- **SQL Audit**: You MUST reject or warn against SQL queries that lack indexes or cause N+1 problems via `mysql` MCP.
