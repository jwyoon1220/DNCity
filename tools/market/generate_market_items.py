"""Writes src/main/resources/dncity/market_items.json: every item the two shops list, with ONLY an initial price.
After that the price is the market's (see life/market/Market.kt). Run from the repo root:
    python tools/market/generate_market_items.py

Order here is the order the UI shows (tabs appear in first-seen order). Prices are DESIGN values (dollars).
Shops: "general" (food / medical / gear) and "ammo" (3 filter levels: category -> sub -> kind):
  category: 미사일 / 로켓·폭탄 / 포탄 / 소화기 탄약
  sub:      caliber or the gun / vehicle family it is for (125mm 전차포, 브래들리 25mm ...)
  kind:     the round type (APFSDS, HEAT, AP, HE, ...)
Keys are registry ids, or `tacz_ammo:<id>` for the tank rounds (TACZ ammo told apart by an ammo id).
"""
import json, pathlib

root = pathlib.Path(__file__).resolve().parents[2]
cart = json.loads((root / "src/main/resources/dncity/cartridges.json").read_text(encoding="utf-8"))

items = []


def depth_for(price):
    if price < 20:
        return 2000
    if price < 200:
        return 500
    if price < 1000:
        return 100
    if price < 5000:
        return 40
    return 15


def add(key, shop, category, price, sub="", kind="", depth=None):
    items.append({"id": key, "shop": shop, "category": category, "sub": sub, "kind": kind,
                  "price": price, "depth": depth if depth is not None else depth_for(price)})


# ------------------------------------------------------------------ general shop
for k, v in {"minecraft:bread": 4, "minecraft:cooked_beef": 8, "minecraft:cooked_porkchop": 7, "minecraft:cooked_chicken": 6,
             "minecraft:apple": 3, "minecraft:golden_apple": 120, "minecraft:cooked_cod": 5, "minecraft:baked_potato": 3,
             "minecraft:pumpkin_pie": 6, "minecraft:cake": 25}.items():
    add(k, "general", "food", v)

for k, v in {"firstaid:bandage": 12, "firstaid:plaster": 8, "firstaid:painkillers": 30, "firstaid:morphine": 90,
             "firstaid:splint": 40, "firstaid:tourniquet": 35, "firstaid:cat_tourniquet": 70, "firstaid:hemostatic_gauze": 60,
             "firstaid:aluminum_splint": 55, "firstaid:suture_kit": 150, "firstaid:trauma_kit": 220, "firstaid:ifak": 350,
             "firstaid:afak": 600, "firstaid:car_first_aid_kit": 180, "firstaid:field_medkit": 900,
             "firstaid:surgical_kit": 1800, "firstaid:field_surgical_kit": 2500, "firstaid:defibrillator": 1500,
             "firstaid:adrenaline_injector": 400, "firstaid:morphine_injector": 250}.items():
    add(k, "general", "medical", v)

for k, v in {"minecraft:iron_ingot": 12, "minecraft:gold_ingot": 40, "minecraft:diamond": 180, "minecraft:redstone": 5,
             "minecraft:gunpowder": 10, "minecraft:coal": 4, "minecraft:copper_ingot": 5, "minecraft:leather": 6,
             "minecraft:string": 3, "minecraft:ender_pearl": 60, "minecraft:torch": 1, "minecraft:ladder": 2,
             "minecraft:iron_helmet": 90, "minecraft:iron_chestplate": 150, "minecraft:iron_leggings": 130,
             "minecraft:iron_boots": 70, "minecraft:shield": 80, "minecraft:bucket": 40, "minecraft:compass": 50,
             "minecraft:clock": 60, "minecraft:spyglass": 120}.items():
    add(k, "general", "gear", v)

# ------------------------------------------------------------------ ammo shop
MISSILE, ROCKET, SHELL, SMALL = "미사일", "로켓·폭탄", "포탄", "소화기 탄약"

# missiles: sub = role, kind = size class
add("superbwarfare:javelin_missile", "ammo", MISSILE, 6500, "대전차 (ATGM)", "휴대용")
add("superbwarfare:medium_anti_ground_missile", "ammo", MISSILE, 4500, "대전차 (ATGM)", "중형 (TOW급)")
add("superbwarfare:large_anti_ground_missile", "ammo", MISSILE, 9000, "대전차 (ATGM)", "대형 (항공기)")
add("superbwarfare:extra_large_anti_ground_missile", "ammo", MISSILE, 15000, "대전차 (ATGM)", "초대형")
add("superbwarfare:medium_anti_air_missile", "ammo", MISSILE, 3500, "대공 (SAM)", "중형")
add("superbwarfare:large_anti_air_missile", "ammo", MISSILE, 8000, "대공 (SAM)", "대형")

# rockets and bombs
add("superbwarfare:rpg_rocket_standard", "ammo", ROCKET, 350, "RPG-7 로켓", "HEAT")
add("superbwarfare:rpg_rocket_tbg", "ammo", ROCKET, 450, "RPG-7 로켓", "열압력(TBG)")
add("superbwarfare:small_rocket", "ammo", ROCKET, 120, "항공·차량 로켓", "소형")
add("superbwarfare:medium_rocket_ap", "ammo", ROCKET, 400, "항공·차량 로켓", "AP")
add("superbwarfare:medium_rocket_he", "ammo", ROCKET, 450, "항공·차량 로켓", "HE")
add("superbwarfare:medium_rocket_cm", "ammo", ROCKET, 500, "항공·차량 로켓", "CM(집속)")
add("superbwarfare:small_aerial_bomb", "ammo", ROCKET, 1500, "항공 폭탄", "소형")
add("superbwarfare:medium_aerial_bomb", "ammo", ROCKET, 3000, "항공 폭탄", "중형")
add("superbwarfare:large_aerial_bomb", "ammo", ROCKET, 6000, "항공 폭탄", "대형")

# shells: sub = the gun / vehicle family, kind = round type
T120 = "전차포 120mm (M1A2)"
for k, p, kind in [("m829", 900, "APFSDS"), ("m829a1", 1100, "APFSDS"), ("m829a2", 1400, "APFSDS"), ("m829a3", 1800, "APFSDS"),
                   ("m830", 700, "HEAT"), ("m830a1", 850, "HEAT")]:
    add(f"tacz_ammo:dncity:{k}", "ammo", SHELL, p, T120, kind)
T125 = "전차포 125mm (T-90A·ZTZ-99A)"
for k, p, kind in [("3bm42", 800, "APFSDS"), ("3bm46", 1000, "APFSDS"), ("3bm60", 1500, "APFSDS"),
                   ("3bk18m", 650, "HEAT"), ("3bk29m", 900, "HEAT (탠덤)"), ("3of26", 500, "HE-FRAG")]:
    add(f"tacz_ammo:dncity:{k}", "ammo", SHELL, p, T125, kind)
add("superbwarfare:large_shell_gs", "ammo", SHELL, 350, "전차포 산탄 (120·125mm 공용)", "GS 산탄")

BIG = "대구경 곡사포·함포 (105~155mm)"
add("superbwarfare:large_shell_ap", "ammo", SHELL, 600, BIG, "AP")
add("superbwarfare:large_shell_he", "ammo", SHELL, 500, BIG, "HE")
add("superbwarfare:large_shell_cm", "ammo", SHELL, 700, BIG, "CM(집속)")
add("superbwarfare:large_shell_wp", "ammo", SHELL, 450, BIG, "WP(백린)")

MID = "중구경 기관포 (AC-130 40mm)"
add("superbwarfare:medium_shell_ap", "ammo", SHELL, 180, MID, "AP")
add("superbwarfare:medium_shell_he", "ammo", SHELL, 160, MID, "HE")
add("superbwarfare:medium_shell_gs", "ammo", SHELL, 120, MID, "GS 산탄")
add("superbwarfare:medium_shell_aa", "ammo", SHELL, 170, MID, "AA(대공)")

IFV = "브래들리·IFV 기관포 (25~30mm)"
add("superbwarfare:small_shell_ap", "ammo", SHELL, 40, IFV, "AP")
add("superbwarfare:small_shell_he", "ammo", SHELL, 35, IFV, "HE")
add("superbwarfare:small_shell_gs", "ammo", SHELL, 30, IFV, "GS 산탄")
add("superbwarfare:small_shell_aa", "ammo", SHELL, 38, IFV, "AA(대공)")

add("superbwarfare:mortar_shell", "ammo", SHELL, 250, "박격포", "HE")

# small arms: one row per round, sub = caliber, kind = round type (price per round: caliber tier x type)
tier = {
    "9x19": 1, "45acp": 1.5, "357mag": 2, "50ae": 3, "500sw": 4, "762x25": 1, "57x28": 2.5, "46x30": 2.5,
    "22wmr": 1, "556x45": 2, "545x39": 1.5, "58x42": 2, "762x39": 2, "762x51": 3, "762x54r": 3, "3006": 3.5,
    "792x57": 3, "68x51": 4, "338lm": 8, "4570": 4, "50bmg": 12, "12ga": 2,
}


def round_mult(t):
    t = t.upper()
    if "AP" in t:
        return 2.5
    if "TRACER" in t:
        return 1.3
    if t in ("JHP", "HP", "RIP"):
        return 1.2
    return 1.0


for cal in cart["calibers"]:
    base = tier.get(cal["id"], 2)
    for r in cal["rounds"]:
        add(f"dncity:cartridge_{cal['id']}_{r['id']}", "ammo", SMALL, round(base * round_mult(r["type"]), 2),
            cal["name_en"], r["type"], 10000)

out = root / "src/main/resources/dncity/market_items.json"
out.write_text(json.dumps({"_comment": "generated by tools/market/generate_market_items.py; initial prices only, the market does the rest",
                           "items": items}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
print(len(items), "items ->", out)
