import React from 'react'
import { useProductCache } from '../contexts/ProductCacheContext'

const InventoryRefreshButton: React.FC = () => {
  const { refreshProducts, isLoading, products, lastFetched } = useProductCache()
  const [isRefreshing, setIsRefreshing] = React.useState(false)

  const handleRefresh = async () => {
    setIsRefreshing(true)
    await refreshProducts()
    setTimeout(() => setIsRefreshing(false), 500)
  }

  const getTimeSinceUpdate = () => {
    if (!lastFetched) return 'Never'
    const seconds = Math.floor((Date.now() - lastFetched) / 1000)
    if (seconds < 60) return `${seconds}s ago`
    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return `${minutes}m ago`
    const hours = Math.floor(minutes / 60)
    return `${hours}h ago`
  }

  return (
    <div className="fixed bottom-4 right-4 z-50">
      <div className="bg-white rounded-lg shadow-lg p-4 flex items-center space-x-3">
        <div className="text-sm">
          <div className="text-gray-600">Inventory Status</div>
          <div className="font-semibold text-gray-900">
            {products.length} products
          </div>
          <div className="text-xs text-gray-500">
            Updated: {getTimeSinceUpdate()}
          </div>
        </div>
        <button
          onClick={handleRefresh}
          disabled={isLoading || isRefreshing}
          className={`px-4 py-2 rounded-lg font-medium transition-all ${
            isLoading || isRefreshing
              ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
              : 'bg-indigo-600 text-white hover:bg-indigo-700 active:scale-95'
          }`}
        >
          <div className="flex items-center space-x-2">
            <svg
              className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
          </div>
        </button>
      </div>
      
      {/* WebSocket status indicator */}
      <div className="mt-2 bg-white rounded-lg shadow-lg p-2 flex items-center justify-between">
        <span className="text-xs text-gray-600">Live Updates</span>
        <div className="flex items-center space-x-1">
          <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
          <span className="text-xs text-green-600 font-medium">Active</span>
        </div>
      </div>
    </div>
  )
}

export default InventoryRefreshButton