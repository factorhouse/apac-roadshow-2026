import React, { useState, useEffect, useRef } from 'react'
import axios from 'axios'
import { EventTracker } from '../services/EventTracker'

interface Message {
  id: string
  text: string
  sender: 'user' | 'assistant'
  timestamp: number
  products?: any[]
}

interface PersonalShopperChatProps {
  sessionId: string
  userId?: string
  basket?: any[]
  currentProduct?: any
}

const PersonalShopperChat: React.FC<PersonalShopperChatProps> = ({
  sessionId,
  userId,
  basket = [],
  currentProduct
}) => {
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      text: "Hi! I'm your personal shopping assistant. I can help you find products, answer questions about items in your cart, and suggest complementary products. How can I help you today?",
      sender: 'assistant',
      timestamp: Date.now()
    }
  ])
  const [inputText, setInputText] = useState('')
  const [isTyping, setIsTyping] = useState(false)
  const [ws, setWs] = useState<WebSocket | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Connect to WebSocket for real-time chat
  useEffect(() => {
    if (isOpen && !ws) {
      const websocket = new WebSocket(`ws://${window.location.host}/ws/chat`)
      
      websocket.onopen = () => {
        console.log('Connected to shopping assistant')
        // Send initial context
        websocket.send(JSON.stringify({
          type: 'INIT',
          sessionId,
          userId,
          basket: basket.map(item => ({
            productId: item.productId,
            name: item.name,
            price: item.price,
            category: item.category
          }))
        }))
      }

      websocket.onmessage = (event) => {
        const data = JSON.parse(event.data)
        if (data.type === 'ASSISTANT_RESPONSE') {
          setMessages(prev => [...prev, {
            id: data.id || Date.now().toString(),
            text: data.text,
            sender: 'assistant',
            timestamp: Date.now(),
            products: data.recommendedProducts
          }])
          setIsTyping(false)
        }
      }

      websocket.onerror = (error) => {
        console.error('WebSocket error:', error)
      }

      setWs(websocket)
    }

    return () => {
      if (ws) {
        ws.close()
      }
    }
  }, [isOpen])

  // Update assistant with basket changes
  useEffect(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'BASKET_UPDATE',
        basket: basket.map(item => ({
          productId: item.productId,
          name: item.name,
          price: item.price,
          category: item.category
        }))
      }))
    }
  }, [basket, ws])

  // Update assistant with current product view
  useEffect(() => {
    if (ws && ws.readyState === WebSocket.OPEN && currentProduct) {
      ws.send(JSON.stringify({
        type: 'PRODUCT_VIEW',
        product: {
          productId: currentProduct.productId,
          name: currentProduct.name,
          price: currentProduct.price,
          category: currentProduct.category,
          description: currentProduct.description
        }
      }))
    }
  }, [currentProduct, ws])

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async () => {
    if (!inputText.trim()) return

    const userMessage: Message = {
      id: Date.now().toString(),
      text: inputText,
      sender: 'user',
      timestamp: Date.now()
    }

    setMessages(prev => [...prev, userMessage])
    setInputText('')
    setIsTyping(true)

    // Track chat interaction
    EventTracker.trackEvent('CHAT_MESSAGE', {
      sessionId,
      messageType: 'user',
      basketSize: basket.length
    })

    // Send via WebSocket if connected, otherwise use REST API
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'USER_MESSAGE',
        text: inputText,
        sessionId,
        userId,
        context: {
          basketItems: basket.length,
          basketValue: basket.reduce((sum, item) => sum + (item.price || 0), 0),
          currentCategory: currentProduct?.category
        }
      }))
    } else {
      // Fallback to REST API
      try {
        const response = await axios.post('/api/chat/shopping-assistant', {
          message: inputText,
          sessionId,
          userId,
          basket,
          currentProduct
        })

        setMessages(prev => [...prev, {
          id: response.data.id || Date.now().toString(),
          text: response.data.response,
          sender: 'assistant',
          timestamp: Date.now(),
          products: response.data.recommendedProducts
        }])
      } catch (error) {
        console.error('Failed to send message:', error)
        setMessages(prev => [...prev, {
          id: Date.now().toString(),
          text: "I'm sorry, I'm having trouble connecting right now. Please try again later.",
          sender: 'assistant',
          timestamp: Date.now()
        }])
      } finally {
        setIsTyping(false)
      }
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const addToCart = async (product: any) => {
    try {
      await axios.post(`/api/ecommerce/cart/${sessionId}/add`, {
        userId,
        productId: product.productId,
        quantity: 1
      })

      EventTracker.trackEvent('ADD_TO_CART_FROM_CHAT', {
        productId: product.productId,
        sessionId
      })

      // Show confirmation in chat
      setMessages(prev => [...prev, {
        id: Date.now().toString(),
        text: `Great! I've added ${product.name} to your cart.`,
        sender: 'assistant',
        timestamp: Date.now()
      }])
    } catch (error) {
      console.error('Failed to add to cart:', error)
    }
  }

  return (
    <>
      {/* Chat Button */}
      {!isOpen && (
        <button
          onClick={() => setIsOpen(true)}
          className="fixed bottom-6 right-6 bg-primary-500 text-white rounded-full p-4 shadow-lg hover:bg-primary-600 transition-all hover:scale-110 z-50"
          aria-label="Open shopping assistant"
        >
          <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
          {basket.length > 0 && (
            <span className="absolute -top-1 -right-1 bg-accent-500 text-white text-xs rounded-full h-5 w-5 flex items-center justify-center">
              {basket.length}
            </span>
          )}
        </button>
      )}

      {/* Chat Widget */}
      {isOpen && (
        <div className="fixed bottom-6 right-6 w-96 h-[600px] bg-white rounded-lg shadow-2xl flex flex-col z-50">
          {/* Header */}
          <div className="bg-primary-500 text-white p-4 rounded-t-lg flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-white/20 rounded-full flex items-center justify-center">
                <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold">Personal Shopping Assistant</h3>
                <p className="text-xs opacity-90">Powered by AI</p>
              </div>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="text-white/80 hover:text-white transition-colors"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Basket Summary */}
          {basket.length > 0 && (
            <div className="bg-gray-50 px-4 py-2 border-b text-sm">
              <span className="text-gray-600">Shopping cart: </span>
              <span className="font-medium">{basket.length} items</span>
              <span className="text-gray-600"> • </span>
              <span className="font-medium">
                ${basket.reduce((sum, item) => sum + (item.price || 0), 0).toFixed(2)}
              </span>
            </div>
          )}

          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex ${message.sender === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[80%] rounded-lg px-4 py-2 ${
                    message.sender === 'user'
                      ? 'bg-primary-500 text-white'
                      : 'bg-gray-100 text-gray-800'
                  }`}
                >
                  <p className="text-sm">{message.text}</p>
                  
                  {/* Product Recommendations */}
                  {message.products && message.products.length > 0 && (
                    <div className="mt-3 space-y-2">
                      {message.products.map((product, idx) => (
                        <div key={idx} className="bg-white rounded p-2 shadow-sm">
                          <div className="flex items-center justify-between">
                            <div className="flex-1">
                              <p className="font-medium text-sm text-gray-900">{product.name}</p>
                              <p className="text-xs text-gray-600">${product.price}</p>
                            </div>
                            <button
                              onClick={() => addToCart(product)}
                              className="text-xs bg-primary-500 text-white px-2 py-1 rounded hover:bg-primary-600 transition-colors"
                            >
                              Add
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
            
            {isTyping && (
              <div className="flex justify-start">
                <div className="bg-gray-100 rounded-lg px-4 py-2">
                  <div className="flex space-x-1">
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
                  </div>
                </div>
              </div>
            )}
            
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Actions */}
          <div className="px-4 py-2 border-t">
            <div className="flex space-x-2 text-xs">
              <button
                onClick={() => setInputText("What goes well with items in my cart?")}
                className="px-3 py-1 bg-gray-100 rounded-full hover:bg-gray-200 transition-colors"
              >
                Suggest complementary items
              </button>
              <button
                onClick={() => setInputText("Any deals today?")}
                className="px-3 py-1 bg-gray-100 rounded-full hover:bg-gray-200 transition-colors"
              >
                Current deals
              </button>
              <button
                onClick={() => setInputText("Help me find...")}
                className="px-3 py-1 bg-gray-100 rounded-full hover:bg-gray-200 transition-colors"
              >
                Search
              </button>
            </div>
          </div>

          {/* Input */}
          <div className="p-4 border-t">
            <div className="flex space-x-2">
              <input
                type="text"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="Ask me anything about shopping..."
                className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
                disabled={isTyping}
              />
              <button
                onClick={sendMessage}
                disabled={isTyping || !inputText.trim()}
                className="bg-primary-500 text-white px-4 py-2 rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default PersonalShopperChat