import os
import re

ctrl_dir = r"d:\VietSpecialty\src\main\java\com\vietspecialty\controller"
results = []

for root, dirs, files in os.walk(ctrl_dir):
    for f in files:
        if f.endswith('.java'):
            filepath = os.path.join(root, f)
            with open(filepath, 'r', encoding='utf-8') as file:
                lines = file.readlines()
            
            base = ""
            for l in lines:
                if "@RequestMapping(" in l:
                    m = re.search(r'"([^"]+)"', l)
                    if m:
                        base = m.group(1)
            
            for i, l in enumerate(lines):
                for verb in ['GetMapping', 'PostMapping', 'PutMapping', 'DeleteMapping']:
                    if '@' + verb in l:
                        sub = ""
                        m = re.search(r'"([^"]*)"', l)
                        if m:
                            sub = m.group(1)
                        endpoint = (base + "/" + sub).replace("//", "/")
                        if not endpoint:
                            endpoint = "/"
                        if endpoint != "/" and endpoint.endswith("/"):
                            endpoint = endpoint[:-1]
                        
                        # Find return statement
                        ret = ""
                        for next_l in lines[i:i+30]:
                            if 'return "' in next_l:
                                m_ret = re.search(r'return "([^"]+)"', next_l)
                                if m_ret:
                                    ret = m_ret.group(1)
                                    break
                            elif 'ResponseEntity' in next_l or '@ResponseBody' in next_l:
                                ret = "JSON/API"
                                break
                        
                        results.append((verb.replace('Mapping', ''), endpoint, f, ret))

print(f"{'VERB':<7} {'ENDPOINT':<42} {'CONTROLLER':<28} {'RETURN/TEMPLATE'}")
print("-" * 105)
for v, ep, f, r in sorted(results, key=lambda x: x[1]):
    print(f"{v:<7} {ep:<42} {f:<28} {r}")

