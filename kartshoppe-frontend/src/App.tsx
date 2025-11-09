import React, { useState, useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import { CartProvider } from './contexts/CartContext'
import { ProductCacheProvider } from './contexts/ProductCacheContext'
import { WebSocketProvider } from './contexts/WebSocketContext'
import { EventTracker } from './services/EventTracker'
import Navbar from './components/Navbar'
import HomePage from './pages/HomePage'
import ProductsPage from './pages/ProductsPage'
import ProductDetailPage from './pages/ProductDetailPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import AnalyticsPage from './pages/AnalyticsPage'
import RecommendationBar from './components/RecommendationBar'
import FeatureEngineeringPanel from './components/FeatureEngineeringPanel'
import PersonalShopperChat from './components/PersonalShopperChat'
import InventoryRefreshButton from './components/InventoryRefreshButton'

function App() {
  const [sessionId] = useState(() => `sess_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`)
  const [userId] = useState(() => `user_${Math.random().toString(36).substr(2, 9)}`)
  const [basket, setBasket] = useState<any[]>([])
  const [currentProduct, setCurrentProduct] = useState<any>(null)

  React.useEffect(() => {
    EventTracker.initialize()
  }, [])

  return (
    <Router>
      <WebSocketProvider>
        <ProductCacheProvider>
          <CartProvider>
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
              <Navbar />
              {/* <RecommendationBar /> */}
              <main className="container mx-auto px-4 py-8">
                <Routes>
                  <Route path="/" element={<HomePage />} />
                  <Route path="/products" element={<ProductsPage />} />
                  <Route path="/product/:id" element={<ProductDetailPage />} />
                  <Route path="/cart" element={<CartPage />} />
                  <Route path="/checkout" element={<CheckoutPage />} />
                  <Route path="/analytics" element={<AnalyticsPage />} />
                </Routes>
              </main>
              <FeatureEngineeringPanel />
              <InventoryRefreshButton />
              {/* <PersonalShopperChat 
                sessionId={sessionId}
                userId={userId}
                basket={basket}
                currentProduct={currentProduct}
              /> */}
            </div>
          </CartProvider>
        </ProductCacheProvider>
      </WebSocketProvider>
    </Router>
  )
}

export default App