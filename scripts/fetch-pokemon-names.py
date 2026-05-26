import requests
import json

result = []
for i in range(1, 1026):
    try:
        r = requests.get(f"https://pokeapi.co/api/v2/pokemon-species/{i}", timeout=10)
        data = r.json()
        en = data["name"].capitalize()
        ko = next((n["name"] for n in data["names"] if n["language"]["name"] == "ko"), None)
        if ko:
            result.append(f"{ko}: {en}")
            print(f"[{i}/1025] {ko} => {en}")
    except Exception as e:
        print(f"[{i}/1025] 실패: {e}")

with open("pokemon-names.yml", "w", encoding="utf-8") as f:
    f.write("names:\n")
    for line in result:
        f.write(f"  {line}\n")

print(f"\n완료: {len(result)}개 → pokemon-names.yml 생성됨")
