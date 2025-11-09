import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import ProductCard from '../components/ProductCard'
import { EventTracker } from '../services/EventTracker'
import RecommendationsSection from '../components/RecommendationsSection'
import CategorySelector from '../components/CategorySelector'
import { useProductCache } from '../contexts/ProductCacheContext'

const HomePage: React.FC = () => {
  const { products, isLoading, getFeaturedProducts } = useProductCache()
  const [featuredProducts, setFeaturedProducts] = useState<any[]>([])
  const [trendingProducts, setTrendingProducts] = useState<any[]>([])
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null)

  useEffect(() => {
    EventTracker.trackPageView('/')
  }, [])

  useEffect(() => {
    // Use cached products for featured and trending
    if (products.length > 0) {
      const featured = getFeaturedProducts()
      setFeaturedProducts(featured.slice(0, 4))
      
      // Get trending as products with high review counts
      const trending = [...products]
        .sort((a, b) => b.reviewCount - a.reviewCount)
        .slice(0, 4)
      setTrendingProducts(trending)
    }
  }, [products, getFeaturedProducts])

  // Remove fetchHomePageData as we use cached products now

  const generateMockProducts = () => {
    const categories = ['Electronics', 'Fashion', 'Home', 'Sports', 'Books']
    const products = []
    
    for (let i = 1; i <= 8; i++) {
      products.push({
        productId: `prod_${i}`,
        name: `Premium Product ${i}`,
        description: `High-quality product with excellent features and great customer reviews.`,
        price: Math.floor(Math.random() * 200) + 20,
        imageUrl: `https://picsum.photos/400/300?random=${i}`,
        category: categories[Math.floor(Math.random() * categories.length)],
        rating: 3 + Math.random() * 2,
        reviewCount: Math.floor(Math.random() * 500) + 10,
        inventory: Math.floor(Math.random() * 50) + 1
      })
    }
    
    return products
  }

  return (
    <div className="space-y-12">
      {/* Hero Section - E-commerce Focused */}
      <section className="relative h-96 rounded-2xl overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-ververica-navy via-primary-800 to-ververica-purple" />
        <div className="absolute inset-0 bg-black/30" />
        
        <div className="relative h-full flex items-center justify-center text-center">
          <div className="max-w-4xl px-4">
            <h1 className="text-6xl font-bold text-white mb-4 animate-fade-in">
              Summer Sale
            </h1>
            <p className="text-2xl text-ververica-bright-teal mb-8 animate-fade-in" style={{ animationDelay: '200ms' }}>
              Up to 50% off on Electronics & Fashion
            </p>
            <div className="flex items-center justify-center space-x-4">
              <Link 
                to="/products" 
                className="inline-block bg-gradient-to-r from-ververica-teal to-ververica-bright-teal text-white font-bold py-3 px-8 rounded-lg hover:shadow-xl transform transition-all duration-200 hover:scale-105 animate-fade-in"
                style={{ animationDelay: '300ms' }}
              >
                Shop Now
              </Link>
              <Link 
                to="/analytics" 
                className="inline-block bg-white/10 backdrop-blur-sm text-white font-bold py-3 px-8 rounded-lg hover:bg-white/20 transform transition-all duration-200 hover:scale-105 animate-fade-in border border-white/30"
                style={{ animationDelay: '400ms' }}
              >
                View Deals
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Category Selector */}
      <CategorySelector 
        selectedCategory={selectedCategory}
        onCategorySelect={setSelectedCategory}
      />

      {/* Recommendations Section */}
      <RecommendationsSection />


      {/* Featured Products */}
      <section>
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-3xl font-bold text-gray-800">Featured Products</h2>
          <Link to="/products" className="text-primary-600 hover:text-primary-700 font-medium">
            View all →
          </Link>
        </div>
        
        {isLoading ? (
          <div className="grid grid-cols-4 gap-6">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="card animate-pulse">
                <div className="h-64 bg-gray-200" />
                <div className="p-4 space-y-3">
                  <div className="h-4 bg-gray-200 rounded" />
                  <div className="h-4 bg-gray-200 rounded w-3/4" />
                  <div className="h-8 bg-gray-200 rounded w-1/2" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-4 gap-6">
            {featuredProducts
              .filter(product => !selectedCategory || product.category === selectedCategory)
              .map((product, index) => (
              <div 
                key={product.productId}
                className="animate-fade-in"
                style={{ animationDelay: `${index * 100}ms` }}
              >
                <ProductCard product={product} />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Trending Products */}
      <section>
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-3xl font-bold text-gray-800">Trending Now</h2>
          <Link to="/products?filter=trending" className="text-primary-600 hover:text-primary-700 font-medium">
            View all →
          </Link>
        </div>
        
        {!isLoading && (
          <div className="grid grid-cols-4 gap-6">
            {trendingProducts
              .filter(product => !selectedCategory || product.category === selectedCategory)
              .map((product, index) => (
              <div 
                key={product.productId}
                className="animate-fade-in"
                style={{ animationDelay: `${index * 100}ms` }}
              >
                <ProductCard product={product} />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* CTA Section */}
      <section className="card p-8 bg-gradient-to-r from-secondary-50 to-primary-50">
        <div className="text-center">
          <h3 className="text-2xl font-bold text-gray-800 mb-4">
            Experience Real-time Shopping
          </h3>
          <p className="text-gray-600 mb-6 max-w-2xl mx-auto">
            Our platform processes thousands of events per second, providing instant recommendations 
            and updates powered by Apache Flink and event sourcing.
          </p>
          <div className="flex justify-center space-x-4">
            <Link to="/products" className="btn-primary">
              Start Shopping
            </Link>
            <button className="btn-secondary">
              Learn More
            </button>
          </div>
        </div>
      </section>
    </div>
  )
}

export default HomePage