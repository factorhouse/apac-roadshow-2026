package com.ververica.composable_job.model.ecommerce;

import java.io.Serializable;
import java.util.List;

public class Product implements Serializable {
    public String productId;
    public String name;
    public String description;
    public double price;
    public String category;
    public String brand;
    public String imageUrl;
    public int inventory;
    public List<String> tags;
    public double rating;
    public int reviewCount;

    public Product() {
    }

    public Product(String productId, String name, String description, double price, 
                   String category, String imageUrl, int inventory, List<String> tags,
                   double rating, int reviewCount) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.brand = null;
        this.imageUrl = imageUrl;
        this.inventory = inventory;
        this.tags = tags;
        this.rating = rating;
        this.reviewCount = reviewCount;
    }
    
    public Product(String productId, String name, String description, double price, 
                   String category, String brand, String imageUrl, int inventory, List<String> tags,
                   double rating, int reviewCount) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.inventory = inventory;
        this.tags = tags;
        this.rating = rating;
        this.reviewCount = reviewCount;
    }
}