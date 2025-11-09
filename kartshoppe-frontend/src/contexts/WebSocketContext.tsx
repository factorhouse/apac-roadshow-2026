import React, { createContext, useContext, useEffect, useState, useRef } from 'react'

interface WebSocketContextType {
  connected: boolean
  sendMessage: (message: any) => void
  lastMessage: any
  recommendations: any[]
  cartUpdates: any[]
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined)

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [connected, setConnected] = useState(false)
  const [lastMessage, setLastMessage] = useState<any>(null)
  const [recommendations, setRecommendations] = useState<any[]>([])
  const [cartUpdates, setCartUpdates] = useState<any[]>([])
  const ws = useRef<WebSocket | null>(null)
  const reconnectTimeout = useRef<NodeJS.Timeout>()

  const connectWebSocket = () => {
    const sessionId = sessionStorage.getItem('sessionId') || generateSessionId()
    const userId = sessionStorage.getItem('userId') || `user_${Date.now()}`

    sessionStorage.setItem('sessionId', sessionId)
    sessionStorage.setItem('userId', userId)

    // Use relative WebSocket URL to go through Vite proxy
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host // Use current host for Vite dev server proxy
    const wsUrl = `${protocol}//${host}/ecommerce/${sessionId}/${userId}`

    console.log(`Connecting WebSocket to: ${wsUrl}`)
    ws.current = new WebSocket(wsUrl)

    ws.current.onopen = () => {
      console.log('✓ WebSocket connected successfully')
      setConnected(true)
      clearTimeout(reconnectTimeout.current)
    }

    ws.current.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        console.log('WebSocket message received:', data.eventType || data.type)
        setLastMessage(data)

        // Dispatch WebSocket message event for ProductCacheContext
        window.dispatchEvent(new CustomEvent('websocket:message', { detail: data }))
        
        switch (data.eventType) {
          case 'RECOMMENDATION':
            setRecommendations(prev => [...prev.slice(-4), data.payload])
            break
          case 'BASKET_RECOMMENDATION':
            // Recommendations from Flink basket analysis job
            console.log('🎯 Received Flink basket recommendation:', data.payload)
            setRecommendations(prev => [...prev.slice(-4), {
              ...data.payload,
              source: 'FLINK_BASKET_ANALYSIS',
              productIds: data.payload.recommendedProducts || []
            }])
            break
          case 'SHOPPING_CART':
            setCartUpdates(prev => [...prev, data.payload])
            break
          case 'PRODUCT_UPDATE':
            // Handle real-time product updates from Kafka Streams cache
            if (window.ProductService) {
              window.ProductService.handleProductUpdate(data.payload)
            }
            break
          case 'PRODUCT_CACHE_SYNC':
            // Bulk update from KStreams cache
            if (window.ProductService && data.payload.products) {
              window.ProductService.updateProductsFromCache(data.payload.products)
            }
            break
        }
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error)
      }
    }

    ws.current.onerror = (error) => {
      console.error('❌ WebSocket error:', error)
      console.log('WebSocket readyState:', ws.current?.readyState)
    }

    ws.current.onclose = (event) => {
      console.warn(`WebSocket closed (code: ${event.code}, reason: ${event.reason || 'none'})`)
      setConnected(false)
      console.log('Reconnecting in 3 seconds...')
      reconnectTimeout.current = setTimeout(connectWebSocket, 3000)
    }
  }

  useEffect(() => {
    connectWebSocket()
    
    return () => {
      clearTimeout(reconnectTimeout.current)
      if (ws.current) {
        ws.current.close()
      }
    }
  }, [])

  const sendMessage = (message: any) => {
    if (ws.current && ws.current.readyState === WebSocket.OPEN) {
      ws.current.send(JSON.stringify(message))
    }
  }

  const generateSessionId = () => {
    return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  return (
    <WebSocketContext.Provider value={{
      connected,
      sendMessage,
      lastMessage,
      recommendations,
      cartUpdates
    }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export const useWebSocket = () => {
  const context = useContext(WebSocketContext)
  if (context === undefined) {
    throw new Error('useWebSocket must be used within a WebSocketProvider')
  }
  return context
}