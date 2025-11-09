import React, { useState, useEffect } from 'react'
import { useWebSocket } from '../contexts/WebSocketContext'

interface Feature {
  name: string
  value: number | string
  type: 'realtime' | 'aggregated' | 'ml-derived'
  description: string
  trend?: 'up' | 'down' | 'stable'
}

const FeatureEngineeringPanel: React.FC = () => {
  const { connected, lastMessage } = useWebSocket()
  const [features, setFeatures] = useState<Feature[]>([])
  const [isExpanded, setIsExpanded] = useState(false)

  useEffect(() => {
    // Simulate real-time feature updates
    const interval = setInterval(() => {
      updateFeatures()
    }, 2000)
    
    return () => clearInterval(interval)
  }, [])

  const updateFeatures = () => {
    const newFeatures: Feature[] = [
      {
        name: 'Items Viewed (10min)',
        value: Math.floor(Math.random() * 20 + 5),
        type: 'realtime',
        description: 'Number of products viewed in last 10 minutes',
        trend: Math.random() > 0.5 ? 'up' : 'down'
      },
      {
        name: 'Avg View Duration',
        value: `${(Math.random() * 30 + 10).toFixed(1)}s`,
        type: 'aggregated',
        description: 'Average time spent viewing products',
        trend: 'stable'
      },
      {
        name: 'Purchase Probability',
        value: `${(Math.random() * 40 + 60).toFixed(1)}%`,
        type: 'ml-derived',
        description: 'ML model prediction for purchase likelihood',
        trend: Math.random() > 0.3 ? 'up' : 'stable'
      },
      {
        name: 'Category Affinity',
        value: 'Electronics',
        type: 'ml-derived',
        description: 'Predicted preferred category',
        trend: 'stable'
      },
      {
        name: 'Click Rate',
        value: `${(Math.random() * 10 + 5).toFixed(2)}%`,
        type: 'realtime',
        description: 'Recommendation click-through rate',
        trend: Math.random() > 0.5 ? 'up' : 'down'
      },
      {
        name: 'Session Depth',
        value: Math.floor(Math.random() * 15 + 3),
        type: 'aggregated',
        description: 'Pages viewed in current session',
        trend: 'up'
      },
      {
        name: 'Fraud Score',
        value: `${(Math.random() * 5).toFixed(1)}%`,
        type: 'ml-derived',
        description: 'Real-time fraud detection score',
        trend: 'stable'
      },
      {
        name: 'Cart Abandonment Risk',
        value: Math.random() > 0.7 ? 'High' : 'Low',
        type: 'ml-derived',
        description: 'Likelihood of cart abandonment',
        trend: Math.random() > 0.5 ? 'up' : 'down'
      }
    ]
    
    setFeatures(newFeatures)
  }

  const getTypeColor = (type: Feature['type']) => {
    switch (type) {
      case 'realtime': return 'from-ververica-teal to-secondary-500'
      case 'aggregated': return 'from-purple-500 to-primary-600'
      case 'ml-derived': return 'from-ververica-bright-teal to-green-500'
      default: return 'from-gray-500 to-gray-600'
    }
  }

  const getTrendIcon = (trend?: Feature['trend']) => {
    switch (trend) {
      case 'up': return '↑'
      case 'down': return '↓'
      case 'stable': return '→'
      default: return ''
    }
  }

  return (
    <div className="fixed bottom-4 right-4 z-40">
      {/* Collapsed View */}
      {!isExpanded && (
        <button
          onClick={() => setIsExpanded(true)}
          className="bg-gradient-to-r from-ververica-navy to-primary-900 text-white px-4 py-3 rounded-lg shadow-xl hover:shadow-2xl transition-all duration-300 flex items-center space-x-3"
        >
          <div className="relative">
            <div className="w-3 h-3 bg-ververica-bright-teal rounded-full animate-pulse"></div>
            <div className="absolute inset-0 w-3 h-3 bg-ververica-bright-teal rounded-full animate-ping"></div>
          </div>
          <span className="font-semibold">Real-Time Features</span>
          <span className="text-xs bg-white/20 px-2 py-1 rounded-full">
            {features.length} active
          </span>
        </button>
      )}

      {/* Expanded View */}
      {isExpanded && (
        <div className="bg-white rounded-2xl shadow-2xl w-96 max-h-[600px] overflow-hidden animate-fade-in-up">
          {/* Header */}
          <div className="bg-gradient-to-r from-ververica-navy to-primary-900 text-white p-4">
            <div className="flex justify-between items-center mb-2">
              <h3 className="text-lg font-bold">Feature Engineering Dashboard</h3>
              <button
                onClick={() => setIsExpanded(false)}
                className="text-white/70 hover:text-white transition-colors"
              >
                ✕
              </button>
            </div>
            <div className="flex items-center space-x-4 text-xs">
              <div className="flex items-center space-x-1">
                <div className={`w-2 h-2 rounded-full ${connected ? 'bg-ververica-bright-teal' : 'bg-red-500'} animate-pulse`}></div>
                <span>{connected ? 'Streaming' : 'Offline'}</span>
              </div>
              <div>Latency: <span className="font-bold">2.3ms</span></div>
              <div>Features/sec: <span className="font-bold">847</span></div>
            </div>
          </div>

          {/* Features Grid */}
          <div className="p-4 max-h-[500px] overflow-y-auto">
            <div className="grid gap-3">
              {features.map((feature, index) => (
                <div
                  key={feature.name}
                  className="border border-gray-200 rounded-lg p-3 hover:shadow-lg transition-all duration-300 animate-fade-in"
                  style={{ animationDelay: `${index * 50}ms` }}
                >
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex-1">
                      <div className="flex items-center space-x-2">
                        <span className="font-semibold text-sm text-gray-900">
                          {feature.name}
                        </span>
                        <span className={`text-xs px-2 py-0.5 rounded-full bg-gradient-to-r ${getTypeColor(feature.type)} text-white`}>
                          {feature.type}
                        </span>
                      </div>
                      <p className="text-xs text-gray-600 mt-1">
                        {feature.description}
                      </p>
                    </div>
                    <div className="text-right">
                      <div className="flex items-center space-x-1">
                        <span className="text-lg font-bold text-gray-900">
                          {feature.value}
                        </span>
                        {feature.trend && (
                          <span className={`text-sm ${
                            feature.trend === 'up' ? 'text-green-500' :
                            feature.trend === 'down' ? 'text-red-500' :
                            'text-gray-500'
                          }`}>
                            {getTrendIcon(feature.trend)}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  
                  {/* Mini visualization */}
                  <div className="h-8 bg-gray-100 rounded overflow-hidden relative">
                    <div 
                      className={`h-full bg-gradient-to-r ${getTypeColor(feature.type)} opacity-30 transition-all duration-1000`}
                      style={{ 
                        width: `${
                          typeof feature.value === 'number' ? 
                          Math.min(100, feature.value * 5) : 
                          Math.random() * 60 + 40
                        }%` 
                      }}
                    />
                    {/* Animated pulse effect */}
                    <div className={`absolute inset-0 bg-gradient-to-r ${getTypeColor(feature.type)} opacity-20 animate-pulse`} />
                  </div>
                </div>
              ))}
            </div>

            {/* Info Section */}
            <div className="mt-4 p-3 bg-gradient-to-r from-ververica-navy/5 to-primary-50 rounded-lg">
              <h4 className="text-xs font-bold text-ververica-navy mb-2">
                About Real-Time Features
              </h4>
              <p className="text-xs text-gray-600">
                Ververica Platform processes millions of events per second, extracting features in real-time 
                for personalization, fraud detection, and recommendation systems. Features are computed with 
                sub-millisecond latency using Apache Flink's stream processing capabilities.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default FeatureEngineeringPanel