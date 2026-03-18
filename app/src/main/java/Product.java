import android.graphics.Bitmap;

public class Product {
    public String name;
    public double amount;
    public String date;
    public String category;
    public int status;
    public Bitmap picture;
    public String productID;
}


/*
*
System Instructions:
You are a product analysis expert. Analyze the attached image and provide the details of the product in a valid JSON format only. Do not include any introductory text or markdown formatting like ```json.

Task:
Identify the product in the image and extract the following information:

product_name: The common name of the product.

shelf_life: The typical time it takes to spoil under standard storage conditions (e.g., "7 days", "12 months").

measurement_unit: State whether it is typically measured in "kg" or "ml".

category: The food category (e.g., Dairy, Meat, Vegetable, Fruit, etc.).

Output Format (JSON):
{
"product_name": "string",
"shelf_life": "string",
"measurement_unit": "string",
"category": "string"
} */