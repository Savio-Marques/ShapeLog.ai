import sys, re, os

def remove_comments(text):
    pattern = re.compile(r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|(/\*.*?\*/|//[^\r\n]*)', re.MULTILINE | re.DOTALL)
    def replacer(match):
        if match.group(2) is not None:
            return ""
        else:
            return match.group(1)
    return pattern.sub(replacer, text)

count = 0
for root, dirs, files in os.walk(r"c:\Users\Sávio\Documents\projetos\telegram\src\main\java"):
    for file in files:
        if file.endswith(".java"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            new_content = remove_comments(content)
            lines = new_content.splitlines()
            final_lines = [line for line in lines if line.strip() != ""]
            with open(path, "w", encoding="utf-8") as f:
                f.write("\n".join(final_lines) + "\n")
            count += 1
print(f"Processed {count} files.")
