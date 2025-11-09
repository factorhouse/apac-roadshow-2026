import axios from 'axios'

export interface Product {
  productId: string
  name: string
  description: string
  price: number
  originalPrice?: number
  discount?: number
  category: string
  imageUrl: string
  images?: string[]
  brand: string
  inventory: number
  tags: string[]
  rating: number
  reviewCount: number
  features?: string[]
  isNew?: boolean
  isFeatured?: boolean
  isSale?: boolean
}

class ProductService {
  private productCache: Map<string, Product> = new Map()
  private updateCallbacks: Set<(products: Product[]) => void> = new Set()

  // Subscribe to product updates
  subscribeToUpdates(callback: (products: Product[]) => void) {
    this.updateCallbacks.add(callback)
    return () => this.updateCallbacks.delete(callback)
  }

  // Process real-time product update from WebSocket
  handleProductUpdate(product: Product) {
    this.productCache.set(product.productId, product)
    this.notifySubscribers()
  }

  // Bulk update products from cache
  updateProductsFromCache(products: Product[]) {
    products.forEach(p => this.productCache.set(p.productId, p))
    this.notifySubscribers()
  }

  private notifySubscribers() {
    const products = Array.from(this.productCache.values())
    this.updateCallbacks.forEach(callback => callback(products))
  }

  async fetchProducts(category?: string, search?: string): Promise<Product[]> {
    try {
      const params: any = {}
      if (category) params.category = category
      if (search) params.search = search
      
      const response = await axios.get('/api/ecommerce/products', { params })
      const products = response.data.map((p: any) => this.enrichProduct(p))
      
      // Update cache
      products.forEach((p: Product) => this.productCache.set(p.productId, p))
      
      return products
    } catch (error) {
      console.error('Failed to fetch products:', error)
      return this.generateMockProducts()
    }
  }

  async fetchProductById(id: string): Promise<Product | null> {
    // Check cache first
    if (this.productCache.has(id)) {
      console.log(`Product ${id} found in cache`)
      return this.productCache.get(id)!
    }

    try {
      console.log(`Fetching product ${id} from API`)
      const response = await axios.get(`/api/ecommerce/product/${id}`)
      const product = this.enrichProduct(response.data)
      this.productCache.set(id, product)
      console.log(`Product ${id} fetched successfully`)
      return product
    } catch (error: any) {
      if (error.response?.status === 404) {
        console.warn(`Product ${id} not found (404) - this product doesn't exist in the backend`)
        return null
      }
      console.error(`Failed to fetch product ${id}:`, error.message || error)
      // Only generate mock as last resort for network errors
      if (!error.response) {
        console.log(`Using mock product for ${id} due to network error`)
        return this.generateMockProduct(id)
      }
      return null
    }
  }

  private enrichProduct(product: any): Product {
    // Use the existing imageUrl from the product or generate a stable placeholder
    const baseImageUrl = product.imageUrl || `https://picsum.photos/600/400?random=${product.productId}`
    const images = [
      baseImageUrl,
      `https://picsum.photos/600/401?random=${product.productId}`,
      `https://picsum.photos/600/402?random=${product.productId}`,
      `https://picsum.photos/600/403?random=${product.productId}`
    ]

    // Calculate discount if price changed
    const hasDiscount = Math.random() > 0.7
    const discount = hasDiscount ? Math.floor(Math.random() * 30) + 10 : 0
    const originalPrice = hasDiscount ? product.price * (1 + discount / 100) : product.price

    return {
      ...product,
      imageUrl: product.imageUrl || baseImageUrl,
      images: images,
      brand: product.brand || this.generateBrand(),
      originalPrice: originalPrice,
      discount: discount,
      features: this.generateFeatures(product.category),
      isNew: Math.random() > 0.8,
      isFeatured: product.rating >= 4.5,
      isSale: hasDiscount
    }
  }

  private generateBrand(): string {
    const brands = ['TechPro', 'StyleCraft', 'HomeEssentials', 'SportMax', 'BookWorm', 
                   'ToyLand', 'BeautyPlus', 'GourmetKitchen', 'EcoLife', 'PremiumCo']
    return brands[Math.floor(Math.random() * brands.length)]
  }

  private generateFeatures(category: string): string[] {
    const featuresByCategory: { [key: string]: string[] } = {
      'Electronics': ['Wireless connectivity', 'Long battery life', 'HD display', 'Fast charging'],
      'Fashion': ['Premium materials', 'Comfortable fit', 'Machine washable', 'Trendy design'],
      'Home & Garden': ['Easy assembly', 'Durable construction', 'Weather resistant', 'Space-saving'],
      'Sports': ['Lightweight', 'Breathable material', 'Non-slip grip', 'UV protection'],
      'Books': ['Bestseller', 'Award winning', 'First edition', 'Signed copy'],
      'default': ['High quality', 'Great value', 'Customer favorite', 'Limited edition']
    }
    
    return featuresByCategory[category] || featuresByCategory['default']
  }

  private generateMockProducts(): Product[] {
    const categories = ['Electronics', 'Fashion', 'Home & Garden', 'Sports', 'Books', 'Toys', 'Beauty', 'Food & Grocery']
    const products: Product[] = []
    
    for (let i = 1; i <= 24; i++) {
      products.push(this.generateMockProduct(`prod_${i}`, categories[i % categories.length]))
    }
    
    return products
  }

  private generateMockProduct(id: string, category?: string): Product {
    const cat = category || 'Electronics'
    const baseImageUrl = `https://picsum.photos/600/400?random=${id}`
    
    return this.enrichProduct({
      productId: id,
      name: `Premium ${cat} Product ${id.slice(-2)}`,
      description: `Experience excellence with our top-rated ${cat.toLowerCase()} product. Crafted with precision and designed for your lifestyle.`,
      price: Math.floor(Math.random() * 500) + 20,
      category: cat,
      imageUrl: baseImageUrl,
      inventory: Math.floor(Math.random() * 100) + 1,
      tags: ['bestseller', 'premium', cat.toLowerCase()],
      rating: 3.5 + Math.random() * 1.5,
      reviewCount: Math.floor(Math.random() * 1000) + 10
    })
  }
}

export default new ProductService()