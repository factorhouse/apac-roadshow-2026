import axios from 'axios'

export class EventTracker {
  private static sessionId: string
  private static userId: string
  private static eventQueue: any[] = []
  private static flushInterval: NodeJS.Timeout

  static initialize() {
    this.sessionId = sessionStorage.getItem('sessionId') || this.generateId('session')
    this.userId = sessionStorage.getItem('userId') || this.generateId('user')
    
    sessionStorage.setItem('sessionId', this.sessionId)
    sessionStorage.setItem('userId', this.userId)

    // Flush events every 2 seconds
    this.flushInterval = setInterval(() => this.flushEvents(), 2000)

    // Track page views
    this.trackPageView()
    
    // Track visibility changes
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.flushEvents()
      }
    })

    // Flush on page unload
    window.addEventListener('beforeunload', () => {
      this.flushEvents()
    })
  }

  static trackEvent(eventType: string, data: any = {}) {
    const event = {
      eventId: this.generateId('event'),
      sessionId: this.sessionId,
      userId: this.userId,
      eventType,
      timestamp: Date.now(),
      ...data,
      metadata: {
        ...data.metadata,
        userAgent: navigator.userAgent,
        referrer: document.referrer,
        url: window.location.href,
        screenResolution: `${window.screen.width}x${window.screen.height}`
      }
    }

    this.eventQueue.push(event)

    // Flush immediately for important events
    if (['CHECKOUT_START', 'ORDER_PLACED', 'ADD_TO_CART'].includes(eventType)) {
      this.flushEvents()
    }
  }

  static trackPageView(page?: string) {
    this.trackEvent('PAGE_VIEW', {
      page: page || window.location.pathname,
      title: document.title
    })
  }

  static trackProductView(productId: string, productName: string, price: number) {
    this.trackEvent('PRODUCT_VIEW', {
      productId,
      productName,
      value: price
    })
  }

  static trackSearch(query: string, resultCount: number) {
    this.trackEvent('PRODUCT_SEARCH', {
      searchQuery: query,
      resultCount
    })
  }

  static trackRecommendationClick(recommendationId: string, productId: string) {
    this.trackEvent('RECOMMENDATION_CLICKED', {
      recommendationId,
      productId
    })
  }

  static getSessionId(): string {
    return this.sessionId
  }

  static getUserId(): string {
    return this.userId
  }

  private static async flushEvents() {
    if (this.eventQueue.length === 0) return

    const events = [...this.eventQueue]
    this.eventQueue = []

    try {
      await axios.post('/api/ecommerce/events', {
        events,
        sessionId: this.sessionId,
        userId: this.userId
      })
    } catch (error) {
      console.error('Failed to send events:', error)
      // Re-add events to queue on failure
      this.eventQueue = [...events, ...this.eventQueue]
    }
  }

  private static generateId(prefix: string): string {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }
}