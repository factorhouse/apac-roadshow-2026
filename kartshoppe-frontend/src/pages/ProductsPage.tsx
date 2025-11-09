import React, { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import ProductCard from '../components/ProductCard'
import { EventTracker } from '../services/EventTracker'
import { useProductCache } from '../contexts/ProductCacheContext'

const ProductsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams()
  const { products, isLoading, error, refreshProducts } = useProductCache()
  const [filteredProducts, setFilteredProducts] = useState<any[]>([])  // Store filtered/sorted products
  const [categories] = useState(['All', 'Electronics', 'Fashion', 'Home & Garden', 'Sports', 'Books', 'Toys', 'Beauty', 'Food & Grocery'])
  const [selectedCategory, setSelectedCategory] = useState('All')
  const [searchQuery, setSearchQuery] = useState('')
  const [sortBy, setSortBy] = useState('featured')
  const [priceRange, setPriceRange] = useState({ min: 0, max: 5000 })

  useEffect(() => {
    EventTracker.trackPageView('/products')
  }, [])

  useEffect(() => {
    applyFiltersAndSort()
  }, [selectedCategory, sortBy, priceRange, products])

  const handleRefresh = async () => {
    await refreshProducts()
  }

  const applyFiltersAndSort = () => {
    let filtered = [...products]

    // Apply category filter
    if (selectedCategory !== 'All') {
      filtered = filtered.filter(p => p.category === selectedCategory)
    }

    // Apply search filter
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase()
      filtered = filtered.filter(p => 
        p.name.toLowerCase().includes(query) || 
        p.description?.toLowerCase().includes(query) ||
        p.brand?.toLowerCase().includes(query) ||
        p.tags?.some((tag: string) => tag.toLowerCase().includes(query))
      )
    }

    // Apply price range filter
    filtered = filtered.filter(p => p.price >= priceRange.min && p.price <= priceRange.max)

    // Apply sorting
    switch (sortBy) {
      case 'price-low':
        filtered.sort((a, b) => a.price - b.price)
        break
      case 'price-high':
        filtered.sort((a, b) => b.price - a.price)
        break
      case 'rating':
        filtered.sort((a, b) => (b.rating || 0) - (a.rating || 0))
        break
      case 'newest':
        filtered.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
        break
      case 'popular':
        filtered.sort((a, b) => (b.reviewCount || 0) - (a.reviewCount || 0))
        break
      default:
        // 'featured' - use default order or custom logic
        break
    }

    setFilteredProducts(filtered)
  }

  const handleSearch = () => {
    EventTracker.trackSearch(searchQuery, filteredProducts.length)
    applyFiltersAndSort()
  }

  const handleCategoryChange = (category: string) => {
    setSelectedCategory(category)
    EventTracker.trackEvent('CATEGORY_BROWSE', { category })
  }

  const handleSortChange = (sort: string) => {
    setSortBy(sort)
    EventTracker.trackEvent('SORT_CHANGE', { sortBy: sort })
  }

  return (
    <div className="space-y-6">
      {/* Search Bar */}
      <div className="card p-6">
        <div className="flex space-x-4">
          <input
            type="text"
            placeholder="Search products..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value)
              if (e.target.value === '') {
                applyFiltersAndSort()
              }
            }}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            className="input-field flex-1"
          />
          <button onClick={handleSearch} className="btn-primary">
            Search
          </button>
          {(searchQuery || selectedCategory !== 'All' || priceRange.min > 0 || priceRange.max < 5000) && (
            <button 
              onClick={() => {
                setSearchQuery('')
                setSelectedCategory('All')
                setPriceRange({ min: 0, max: 5000 })
                setSortBy('featured')
              }}
              className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
            >
              Clear All
            </button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="space-y-4">
        {/* Category Filter */}
        <div className="flex flex-wrap gap-2">
          {categories.map((category) => (
            <button
              key={category}
              onClick={() => handleCategoryChange(category)}
              className={`px-4 py-2 rounded-lg font-medium transition-all ${
                selectedCategory === category
                  ? 'bg-primary-500 text-white'
                  : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-200'
              }`}
            >
              {category}
              {selectedCategory === category && products.length > 0 && (
                <span className="ml-2 text-xs opacity-75">
                  ({filteredProducts.length})
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Sort and Price Range */}
        <div className="flex justify-between items-center">
          <div className="flex items-center space-x-4">
            <div>
              <label className="text-sm text-gray-600 mb-1 block">Sort by</label>
              <select
                value={sortBy}
                onChange={(e) => handleSortChange(e.target.value)}
                className="input-field w-48"
              >
                <option value="featured">Featured</option>
                <option value="price-low">Price: Low to High</option>
                <option value="price-high">Price: High to Low</option>
                <option value="rating">Highest Rated</option>
                <option value="popular">Most Popular</option>
                <option value="newest">Newest First</option>
              </select>
            </div>

            <div className="flex items-center space-x-2">
              <label className="text-sm text-gray-600">Price Range:</label>
              <input
                type="number"
                placeholder="Min"
                value={priceRange.min}
                onChange={(e) => setPriceRange({ ...priceRange, min: Number(e.target.value) })}
                className="input-field w-24"
              />
              <span className="text-gray-500">-</span>
              <input
                type="number"
                placeholder="Max"
                value={priceRange.max}
                onChange={(e) => setPriceRange({ ...priceRange, max: Number(e.target.value) })}
                className="input-field w-24"
              />
            </div>
          </div>

          <div className="text-sm text-gray-600">
            Showing {filteredProducts.length} of {products.length} products
          </div>
        </div>
      </div>

      {/* Product Grid */}
      {isLoading ? (
        <div className="grid grid-cols-4 gap-6">
          {[...Array(8)].map((_, i) => (
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
      ) : products.length === 0 && !error ? (
        <div className="card p-12 text-center bg-gradient-to-br from-blue-50 to-purple-50">
          <div className="text-6xl mb-4">🏪</div>
          <h2 className="text-3xl font-bold text-gray-900 mb-4">Shop is Empty</h2>
          <p className="text-lg text-gray-700 mb-2">The shop starts empty in this event-driven architecture.</p>
          <p className="text-gray-600 mb-6">Products will stream in as the Flink inventory job processes them.</p>

          <div className="max-w-2xl mx-auto bg-white rounded-lg p-6 shadow-md mb-6">
            <h3 className="text-xl font-semibold mb-3 text-gray-900">🚀 To populate products:</h3>
            <div className="bg-gray-900 text-green-400 font-mono text-sm p-4 rounded-md text-left">
              ./flink-1-inventory-job.sh
            </div>
            <p className="text-sm text-gray-600 mt-3">
              This will read initial products from <code className="bg-gray-100 px-2 py-1 rounded">data/initial-products.json</code>
              and stream them into the shop via Kafka
            </p>
          </div>

          <div className="flex items-center justify-center space-x-2 text-sm text-gray-600">
            <div className="animate-pulse w-2 h-2 bg-blue-500 rounded-full"></div>
            <span>Watching for products...</span>
          </div>

          <button onClick={handleRefresh} className="mt-4 btn-primary">
            Refresh
          </button>
        </div>
      ) : filteredProducts.length === 0 ? (
        <div className="card p-12 text-center">
          <p className="text-gray-500">No products match your filters</p>
          {selectedCategory !== 'All' && (
            <button
              onClick={() => setSelectedCategory('All')}
              className="mt-4 btn-primary"
            >
              Clear Filters
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {filteredProducts.map((product, index) => (
            <div
              key={product.productId}
              className="animate-fade-in"
              style={{ animationDelay: `${Math.min(index * 50, 500)}ms` }}
            >
              <ProductCard product={product} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default ProductsPage