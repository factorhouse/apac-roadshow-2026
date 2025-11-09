class ImageCacheService {
  private imageCache: Map<string, string> = new Map()
  private loadingImages: Set<string> = new Set()
  private defaultImages: Map<string, string> = new Map()
  
  constructor() {
    this.initializeDefaultImages()
  }

  private initializeDefaultImages() {
    // Create data URLs for fallback images (simple SVG placeholders)
    const categories = ['Electronics', 'Fashion', 'Home & Garden', 'Sports', 'Books', 'Toys', 'Beauty', 'Food & Grocery']
    
    categories.forEach(category => {
      const svg = this.createPlaceholderSVG(category)
      const dataUrl = `data:image/svg+xml;base64,${btoa(svg)}`
      this.defaultImages.set(category, dataUrl)
    })
    
    // Set a generic default
    const defaultSvg = this.createPlaceholderSVG('Product')
    this.defaultImages.set('default', `data:image/svg+xml;base64,${btoa(defaultSvg)}`)
  }

  private createPlaceholderSVG(text: string): string {
    const colors = {
      'Electronics': '#2563eb',
      'Fashion': '#ec4899',
      'Home & Garden': '#10b981',
      'Sports': '#f97316',
      'Books': '#6366f1',
      'Toys': '#f59e0b',
      'Beauty': '#d946ef',
      'Food & Grocery': '#84cc16',
      'Product': '#6b7280'
    }
    
    const color = colors[text as keyof typeof colors] || colors.Product
    
    return `
      <svg width="400" height="300" xmlns="http://www.w3.org/2000/svg">
        <rect width="400" height="300" fill="${color}20"/>
        <rect x="150" y="100" width="100" height="100" rx="10" fill="${color}40"/>
        <text x="200" y="230" font-family="Arial, sans-serif" font-size="18" fill="${color}" text-anchor="middle">
          ${text}
        </text>
      </svg>
    `
  }

  getImageUrl(productId: string, originalUrl: string | undefined, category: string = 'Product'): string {
    // Check if we have a cached version
    const cacheKey = productId || originalUrl || category
    if (this.imageCache.has(cacheKey)) {
      return this.imageCache.get(cacheKey)!
    }

    // If no original URL, return category placeholder
    if (!originalUrl) {
      const placeholder = this.defaultImages.get(category) || this.defaultImages.get('default')!
      this.imageCache.set(cacheKey, placeholder)
      return placeholder
    }

    // Check if it's already a data URL or local asset
    if (originalUrl.startsWith('data:') || originalUrl.startsWith('/')) {
      this.imageCache.set(cacheKey, originalUrl)
      return originalUrl
    }

    // For external URLs, we'll try to load them but provide immediate fallback
    if (!this.loadingImages.has(cacheKey)) {
      this.loadingImages.add(cacheKey)
      this.preloadImage(originalUrl, cacheKey, category)
    }

    // Return the original URL for now (browser will cache it)
    return originalUrl
  }

  private async preloadImage(url: string, cacheKey: string, category: string): Promise<void> {
    try {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      
      await new Promise((resolve, reject) => {
        img.onload = resolve
        img.onerror = reject
        img.src = url
        
        // Set a timeout to avoid waiting forever
        setTimeout(reject, 5000)
      })
      
      // Successfully loaded, cache the URL
      this.imageCache.set(cacheKey, url)
    } catch (error) {
      // Failed to load, use category placeholder
      console.warn(`Failed to load image: ${url}, using placeholder`)
      const placeholder = this.defaultImages.get(category) || this.defaultImages.get('default')!
      this.imageCache.set(cacheKey, placeholder)
    } finally {
      this.loadingImages.delete(cacheKey)
    }
  }

  handleImageError(event: React.SyntheticEvent<HTMLImageElement>, productId: string, category: string = 'Product') {
    const img = event.target as HTMLImageElement
    const placeholder = this.defaultImages.get(category) || this.defaultImages.get('default')!
    
    // Cache the placeholder for this product
    this.imageCache.set(productId, placeholder)
    
    // Update the image source
    img.src = placeholder
  }

  clearCache() {
    this.imageCache.clear()
    this.loadingImages.clear()
  }

  getCacheStats() {
    return {
      cachedImages: this.imageCache.size,
      loadingImages: this.loadingImages.size,
      totalSize: this.imageCache.size + this.loadingImages.size
    }
  }
}

export default new ImageCacheService()