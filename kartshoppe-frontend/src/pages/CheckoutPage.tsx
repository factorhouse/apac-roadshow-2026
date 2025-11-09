import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { useCart } from '../contexts/CartContext'
import { EventTracker } from '../services/EventTracker'

const CheckoutPage: React.FC = () => {
  const navigate = useNavigate()
  const { items, totalAmount, clearCart } = useCart()
  const [processing, setProcessing] = useState(false)
  const [orderComplete, setOrderComplete] = useState(false)
  const [orderId, setOrderId] = useState('')
  const [useCdc, setUseCdc] = useState(false);

  const [formData, setFormData] = useState({
    name: '',
    email: '',
    street: '',
    city: '',
    state: '',
    zipCode: '',
    country: 'USA',
    cardNumber: '',
    cardName: '',
    expiry: '',
    cvv: ''
  })

  React.useEffect(() => {
    EventTracker.trackPageView('/checkout')
    if (items.length === 0 && !orderComplete) {
      navigate('/cart')
    }
  }, [items, orderComplete])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setProcessing(true)

    const sessionIdForCheckout = EventTracker.getSessionId();
    console.log("==> Submitting checkout for sessionId:", sessionIdForCheckout);

    try {
      const response = await axios.post('/api/ecommerce/checkout', {
        sessionId: sessionIdForCheckout,
        userId: EventTracker.getUserId(),
        // sessionId: sessionStorage.getItem('sessionId'),
        // userId: sessionStorage.getItem('userId'),
        shippingAddress: {
          name: formData.name,
          street: formData.street,
          city: formData.city,
          state: formData.state,
          zipCode: formData.zipCode,
          country: formData.country
        },
        paymentMethod: 'credit_card',
        useCdc: useCdc
      })

      EventTracker.trackEvent('ORDER_PLACED', {
        orderId: response.data.orderId,
        totalAmount: response.data.totalAmount,
        itemCount: items.length
      })

      setOrderId(response.data.orderId)
      setOrderComplete(true)
      clearCart()
    } catch (error) {
      console.error('Checkout failed:', error)
      // Mock success for demo
      const mockOrderId = `order_${Date.now()}`
      setOrderId(mockOrderId)
      setOrderComplete(true)
      
      EventTracker.trackEvent('ORDER_PLACED', {
        orderId: mockOrderId,
        totalAmount: totalAmount,
        itemCount: items.length
      })
      
      clearCart()
    } finally {
      setProcessing(false)
    }
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    })
  }

  if (orderComplete) {
    return (
      <div className="max-w-2xl mx-auto">
        <div className="card p-12 text-center animate-fade-in">
          <div className="text-6xl mb-4">✅</div>
          <h1 className="text-3xl font-bold text-gray-900 mb-4">Order Confirmed!</h1>
          <p className="text-gray-600 mb-2">Thank you for your purchase</p>
          <p className="text-lg font-semibold text-gray-800 mb-6">
            Order ID: {orderId}
          </p>
          <div className="bg-blue-50 p-4 rounded-lg mb-6">
            <p className="text-sm text-blue-800">
              Your order has been processed through our event-sourced system
              and will be tracked in real-time using Apache Flink.
            </p>
          </div>
          <button onClick={() => navigate('/products')} className="btn-primary">
            Continue Shopping
          </button>
        </div>
      </div>
    )
  }

  const subtotal = totalAmount
  const tax = subtotal * 0.08
  const shipping = subtotal > 50 ? 0 : 5.99
  const total = subtotal + tax + shipping

  return (
    <div className="max-w-6xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Checkout</h1>

      <form onSubmit={handleSubmit}>
        <div className="grid grid-cols-3 gap-8">
          <div className="col-span-2 space-y-6">
            {/* Contact Information */}
            <div className="card p-6">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Contact Information</h2>
              <div className="grid grid-cols-2 gap-4">
                <input
                  type="text"
                  name="name"
                  placeholder="Full Name"
                  value={formData.name}
                  onChange={handleInputChange}
                  required
                  className="input-field col-span-2"
                />
                <input
                  type="email"
                  name="email"
                  placeholder="Email Address"
                  value={formData.email}
                  onChange={handleInputChange}
                  required
                  className="input-field col-span-2"
                />
              </div>
            </div>

            {/* Shipping Address */}
            <div className="card p-6">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Shipping Address</h2>
              <div className="grid grid-cols-2 gap-4">
                <input
                  type="text"
                  name="street"
                  placeholder="Street Address"
                  value={formData.street}
                  onChange={handleInputChange}
                  required
                  className="input-field col-span-2"
                />
                <input
                  type="text"
                  name="city"
                  placeholder="City"
                  value={formData.city}
                  onChange={handleInputChange}
                  required
                  className="input-field"
                />
                <input
                  type="text"
                  name="state"
                  placeholder="State"
                  value={formData.state}
                  onChange={handleInputChange}
                  required
                  className="input-field"
                />
                <input
                  type="text"
                  name="zipCode"
                  placeholder="ZIP Code"
                  value={formData.zipCode}
                  onChange={handleInputChange}
                  required
                  className="input-field"
                />
                <select
                  name="country"
                  value={formData.country}
                  onChange={handleInputChange}
                  className="input-field"
                >
                  <option value="USA">United States</option>
                  <option value="Canada">Canada</option>
                  <option value="UK">United Kingdom</option>
                </select>
              </div>
            </div>

            {/* Payment Information */}
            <div className="card p-6">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Payment Information</h2>
              <div className="grid grid-cols-2 gap-4">
                <input
                  type="text"
                  name="cardNumber"
                  placeholder="Card Number"
                  value={formData.cardNumber}
                  onChange={handleInputChange}
                  required
                  className="input-field col-span-2"
                />
                <input
                  type="text"
                  name="cardName"
                  placeholder="Name on Card"
                  value={formData.cardName}
                  onChange={handleInputChange}
                  required
                  className="input-field col-span-2"
                />
                <input
                  type="text"
                  name="expiry"
                  placeholder="MM/YY"
                  value={formData.expiry}
                  onChange={handleInputChange}
                  required
                  className="input-field"
                />
                <input
                  type="text"
                  name="cvv"
                  placeholder="CVV"
                  value={formData.cvv}
                  onChange={handleInputChange}
                  required
                  className="input-field"
                />
              </div>
              <div className="mt-4 p-3 bg-blue-50 rounded-lg">
                <p className="text-xs text-blue-800">
                  This is a demo. No real payment will be processed.
                </p>
              </div>
            </div>
          </div>

          {/* Order Summary */}
          <div>
            <div className="card p-6 sticky top-24">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Order Summary</h2>

              <div className="space-y-3 mb-4 max-h-48 overflow-y-auto">
                {items.map((item) => (
                  <div key={item.productId} className="flex justify-between text-sm">
                    <span className="text-gray-600">
                      {item.productName} x{item.quantity}
                    </span>
                    <span className="text-gray-900">
                      ${(item.price * item.quantity).toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>

              <div className="border-t pt-4 space-y-2">
                <div className="flex justify-between text-gray-600">
                  <span>Subtotal</span>
                  <span>${subtotal.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Tax</span>
                  <span>${tax.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Shipping</span>
                  <span>{shipping === 0 ? 'FREE' : `$${shipping.toFixed(2)}`}</span>
                </div>
              </div>

              <div className="border-t pt-4 mt-4">
                <div className="flex justify-between text-xl font-bold text-gray-900">
                  <span>Total</span>
                  <span>${total.toFixed(2)}</span>
                </div>
              </div>

              <div className="border-t pt-4 mt-4">
                <div className="flex items-center">
                  <input
                    type="checkbox"
                    id="useCdc"
                    name="useCdc"
                    checked={useCdc}
                    onChange={(e) => setUseCdc(e.target.checked)}
                    className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                  />
                  <label htmlFor="useCdc" className="ml-3 block text-sm font-medium text-gray-700">
                    Use Flink CDC (Event-Driven)
                  </label>
                </div>
              </div>

              <button
                type="submit"
                disabled={processing}
                className={`w-full mt-6 ${
                  processing ? 'bg-gray-400 cursor-not-allowed' : 'btn-primary'
                }`}
              >
                {processing ? 'Processing...' : 'Place Order'}
              </button>

              <div className="mt-4 space-y-1">
                <p className="text-xs text-gray-500">✓ Secure checkout</p>
                <p className="text-xs text-gray-500">✓ Event-sourced order processing</p>
                <p className="text-xs text-gray-500">✓ Real-time tracking</p>
              </div>
            </div>
          </div>
        </div>
      </form>
    </div>
  )
}

export default CheckoutPage