import os
from PIL import Image

src_path = r"C:\Users\lisil\.gemini\antigravity\brain\f662a421-cd46-4c95-99f5-3bd4d3e50c28\app_icon_liquid_tail_1792530723623.jpg"
res_dir = r"c:\Users\lisil\Documents\AI\BrainStorm\app\src\main\res"

img = Image.open(src_path).convert("RGBA")

# 1. Save main drawable for in-app logo
drawable_dir = os.path.join(res_dir, "drawable")
os.makedirs(drawable_dir, exist_ok=True)
img.resize((512, 512), Image.Resampling.LANCZOS).save(os.path.join(drawable_dir, "ic_app_logo_liquid.png"), "PNG")
print("Saved ic_app_logo_liquid.png")

# 2. Save mipmaps
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

for folder, size in densities.items():
    target_dir = os.path.join(res_dir, folder)
    os.makedirs(target_dir, exist_ok=True)
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(target_dir, "ic_launcher.png"), "PNG")
    resized.save(os.path.join(target_dir, "ic_launcher_round.png"), "PNG")
    print(f"Saved {folder} ({size}x{size})")

# 3. For adaptive icon foreground
fg = img.resize((432, 432), Image.Resampling.LANCZOS)
fg.save(os.path.join(drawable_dir, "ic_fox_foreground.png"), "PNG")
print("Saved ic_fox_foreground.png")
