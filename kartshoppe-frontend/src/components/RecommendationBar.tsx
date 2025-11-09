import React from 'react'
import { useWebSocket } from '../contexts/WebSocketContext'
import { EventTracker } from '../services/EventTracker'

const RecommendationBar: React.FC = () => {
  const { recommendations } = useWebSocket()

  if (recommendations.length === 0) return null

  const latestRecommendation = recommendations[recommendations.length - 1]

  const handleClick = (productId: string) => {
    EventTracker.trackRecommendationClick(latestRecommendation.recommendationId, productId)
  }

  return (
    <div className="bg-gradient-to-r from-secondary-50 to-primary-50 border-b border-gray-200">
      <div className="container mx-auto px-4 py-3">
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2">
            <svg className="w-5 h-5 text-secondary-600 animate-pulse" fill="currentColor" viewBox="0 0 20 20">
              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
            </svg>
            <span className="text-sm font-semibold text-gray-700">
              {latestRecommendation.reason || 'Recommended for you'}
            </span>
          </div>
          
          <div className="flex-1 flex items-center space-x-3 overflow-x-auto">
            {latestRecommendation.productIds?.slice(0, 5).map((productId: string, index: number) => (
              <button
                key={productId}
                onClick={() => handleClick(productId)}
                className="flex-shrink-0 px-3 py-1 bg-white rounded-full text-sm font-medium text-gray-700 hover:bg-secondary-100 hover:text-secondary-700 transition-colors animate-fade-in"
                style={{ animationDelay: `${index * 100}ms` }}
              >
                View Product #{productId.slice(-4)}
              </button>
            ))}
          </div>

          <div className="flex items-center space-x-1">
            <span className="text-xs text-gray-500">Confidence:</span>
            <div className="w-20 h-2 bg-gray-200 rounded-full overflow-hidden">
              <div 
                className="h-full bg-gradient-to-r from-secondary-400 to-secondary-600 transition-all duration-500"
                style={{ width: `${(latestRecommendation.confidence || 0) * 100}%` }}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default RecommendationBar