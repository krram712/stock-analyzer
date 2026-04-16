import re

file = r'C:\Users\raghu\Downloads\code\stock-analyzer\frontend\src\screens\SearchScreen.jsx'
with open(file, 'r', encoding='utf-8') as f:
    content = f.read()

print(f"File size: {len(content)} chars")

# Find lines 130-170 area - the date selector block
# Match from {/* Date selector */} through the closing </div> before {validationError
pattern = r'\{/\* Date selector \*/\}.*?</div>(?=\s*\n\s*\{validationError)'
m = re.search(pattern, content, re.DOTALL)
if m:
    print(f"Found match at {m.start()}-{m.end()}")
    print("First 100 chars:", repr(m.group()[:100]))
else:
    # Try a broader search
    idx = content.find('Date selector')
    print(f"'Date selector' found at index: {idx}")
    if idx >= 0:
        print("Context:", repr(content[idx-10:idx+200]))

