import json
from pathlib import Path

script_dir = Path(__file__).parent
json_file_path = script_dir.parent / "data" / "initial-products.json"

print("-- ====================================================================")
print("-- AUTO-GENERATED PRODUCT AND INVENTORY DATA")
print(f"-- Source: {json_file_path}")
print("-- ====================================================================\n")

try:
    # --- Read the JSON data ---
    with open(json_file_path, 'r') as f:
        products = json.load(f)

    # --- Generate SQL for PRODUCTS table ---
    print("-- 1. PRODUCTS DATA --")
    print("INSERT INTO products (product_id, product_name, category, brand, price, description, image_url, is_active) VALUES")
    
    product_value_strings = []
    for product in products:
        # Escape single quotes to prevent SQL errors
        product_id = product.get('productId', 'N/A').replace("'", "''")
        product_name = product.get('name', 'No Name').replace("'", "''")
        category = product.get('category', 'Uncategorized').replace("'", "''")
        brand = product.get('brand', 'N/A').replace("'", "''")
        description = product.get('description', '').replace("'", "''")
        image_url = product.get('imageUrl', '')
        price = product.get('price', 0.0)

        product_value_strings.append(
            f"    ('{product_id}', '{product_name}', '{category}', '{brand}', {price}, '{description}', '{image_url}', true)"
        )
    
    # Join all the value strings with commas and print
    print(",\n".join(product_value_strings) + ";\n")


    # --- Generate SQL for INVENTORY table ---
    print("-- 2. INVENTORY DATA --")
    print("INSERT INTO inventory (product_id, quantity_on_hand, quantity_reserved, reorder_point, reorder_quantity) VALUES")
    
    inventory_value_strings = []
    for product in products:
        product_id = product.get('productId', 'N/A').replace("'", "''")
        quantity_on_hand = product.get('inventory', 0) 
        
        inventory_value_strings.append(
            f"    ('{product_id}', {quantity_on_hand}, 0, 10, 20)"
        )
    
    # Join all the value strings with commas and print
    print(",\n".join(inventory_value_strings) + ";\n")

except FileNotFoundError:
    print(f"-- ERROR: Could not find the JSON file at '{json_file_path}'")
except json.JSONDecodeError:
    print("-- ERROR: Could not parse the JSON file. Please check for syntax errors.")
