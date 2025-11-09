import React, { useEffect, useState } from 'react'
import { useWebSocket } from '../contexts/WebSocketContext'
import { EventTracker } from '../services/EventTracker'
import ProductService, { Product } from '../services/ProductService'
import ImageCacheService from '../services/ImageCacheService'
import { useNavigate } from 'react-router-dom'

const RecommendationsSection: React.FC = () => {
  const { recommendations, connected } = useWebSocket()
  const [recommendedProducts, setRecommendedProducts] = useState<Product[]>([])
  const navigate = useNavigate()

  useEffect(() => {
    if (recommendations.length > 0) {
      const latestRec = recommendations[recommendations.length - 1]
      loadRecommendedProducts(latestRec.productIds || [])
    }
  }, [recommendations])

  const loadRecommendedProducts = async (productIds: string[]) => {
    try {
      console.log('Loading recommended products:', productIds)
      const products = await Promise.all(
        productIds.slice(0, 4).map(async (id) => {
          try {
            return await ProductService.fetchProductById(id)
          } catch (error) {
            console.warn(`Failed to fetch product ${id}:`, error)
            return null
          }
        })
      )
      const validProducts = products.filter(p => p !== null) as Product[]
      console.log(`Loaded ${validProducts.length} out of ${productIds.length} recommended products`)
      setRecommendedProducts(validProducts)
    } catch (error) {
      console.error('Error loading recommended products:', error)
      setRecommendedProducts([])
    }
  }

  const handleProductClick = (product: Product) => {
    if (recommendations.length > 0) {
      const latestRec = recommendations[recommendations.length - 1]
      EventTracker.trackRecommendationClick(latestRec.recommendationId, product.productId)
    }
    navigate(`/product/${product.productId}`)
  }

  if (recommendedProducts.length === 0) return null

  const latestRec = recommendations[recommendations.length - 1]
  const isFlinkRec = latestRec?.source === 'FLINK_BASKET_ANALYSIS'
  const recType = latestRec?.recommendationType || 'PERSONALIZED'

  return (
    <div className="bg-gradient-to-br from-primary-50 via-white to-secondary-50 py-8">
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center space-x-3">
            <div className="relative">
              <div className="absolute inset-0 bg-gradient-to-r from-primary-400 to-secondary-400 rounded-full animate-pulse opacity-75"></div>
              <div className="relative bg-white rounded-full p-3">
                <svg className="w-6 h-6 text-primary-600" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                </svg>
              </div>
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-2xl font-bold text-gray-900">
                  {isFlinkRec ? '🤖 AI Basket Analysis' : 'Personalized for You'}
                </h2>
                {isFlinkRec && (
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gradient-to-r from-purple-100 to-pink-100 text-purple-800">
                    Flink Pattern Mining
                  </span>
                )}
              </div>
              <p className="text-sm text-gray-600">
                {isFlinkRec
                  ? `${recType.replace(/_/g, ' ')} • ${latestRec.context?.basketSize || ''} items in cart`
                  : (latestRec?.reason || 'Based on your browsing history')}
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            {connected && (
              <div className="flex items-center space-x-2">
                <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                <span className="text-xs text-gray-600">Real-time updates</span>
              </div>
            )}
            <div className="flex items-center space-x-2">
              <span className="text-xs text-gray-600">
                {isFlinkRec ? 'Pattern Confidence' : 'Confidence'}
              </span>
              <div className="w-24 h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-primary-400 to-primary-600 transition-all duration-500"
                  style={{ width: `${(latestRec?.confidence || 0) * 100}%` }}
                />
              </div>
              <span className="text-xs font-semibold text-gray-700">
                {Math.round((latestRec?.confidence || 0) * 100)}%
              </span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {recommendedProducts.map((product, index) => (
            <div
              key={product.productId}
              className="group relative bg-white rounded-xl shadow-md hover:shadow-2xl transition-all duration-300 transform hover:-translate-y-1 cursor-pointer overflow-hidden animate-fade-in-up"
              style={{ animationDelay: `${index * 100}ms` }}
              onClick={() => handleProductClick(product)}
            >
              {/* Recommendation Badge */}
              <div className="absolute top-2 left-2 z-10 bg-gradient-to-r from-primary-500 to-secondary-500 text-white px-3 py-1 rounded-full text-xs font-semibold shadow-lg">
                AI Pick
              </div>

              {/* Product Image */}
              <div className="relative h-48 overflow-hidden bg-gray-100">
                <img
                  src={ImageCacheService.getImageUrl(product.productId, product.imageUrl, product.category)}
                  alt={product.name}
                  className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                  onError={(e) => ImageCacheService.handleImageError(e, product.productId, product.category)}
                />
                {product.discount && product.discount > 0 && (
                  <div className="absolute top-2 right-2 bg-red-500 text-white px-2 py-1 rounded-md text-xs font-bold">
                    -{product.discount}%
                  </div>
                )}
              </div>

              {/* Product Info */}
              <div className="p-4">
                <h3 className="text-lg font-semibold text-gray-900 group-hover:text-primary-600 transition-colors line-clamp-2">
                  {product.name}
                </h3>
                
                <div className="mt-2 flex items-center justify-between">
                  <div>
                    <span className="text-xl font-bold text-gray-900">
                      ${product.price.toFixed(2)}
                    </span>
                    {product.originalPrice && product.originalPrice > product.price && (
                      <span className="ml-2 text-sm text-gray-500 line-through">
                        ${product.originalPrice.toFixed(2)}
                      </span>
                    )}
                  </div>
                  
                  <div className="flex items-center space-x-1">
                    <svg className="w-4 h-4 text-yellow-400" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                    </svg>
                    <span className="text-sm text-gray-600">{product.rating.toFixed(1)}</span>
                  </div>
                </div>

                <button className="mt-3 w-full bg-gradient-to-r from-primary-500 to-primary-600 text-white py-2 px-4 rounded-lg font-semibold hover:from-primary-600 hover:to-primary-700 transition-all duration-200 transform hover:scale-105 active:scale-95">
                  Quick View
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* Loading Animation for New Recommendations */}
        {recommendations.length > 0 && recommendedProducts.length === 0 && (
          <div className="flex justify-center items-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
          </div>
        )}
      </div>
    </div>
  )
}

export default RecommendationsSection