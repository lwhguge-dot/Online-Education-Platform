
import os

pom_path = r'c:\Users\XuShuang\Desktop\demo\backend\pom.xml'

with open(pom_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add LangChain4j properties if not already there (though we already added one part)
# Actually, the property was already added successfully in the last step.

# Add to Dependency Management
dep_mgmt_marker = '</dependencies>\n    </dependencyManagement>'
new_deps = """            <!-- LangChain4j BOM -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-bom</artifactId>
                <version>${langchain4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- LangChain4j Spring Boot Starter -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-spring-boot-starter</artifactId>
                <version>${langchain4j.version}</version>
            </dependency>\n        """

if 'langchain4j-bom' not in content:
    # We look for the last </dependencies> inside <dependencyManagement>
    # Since there are multiple </dependencies>, we find the one before </dependencyManagement>
    content = content.replace(dep_mgmt_marker, new_deps + dep_mgmt_marker)

with open(pom_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("POM updated successfully")
