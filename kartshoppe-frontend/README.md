# KartShoppe Frontend

A modern e-commerce frontend built with React, TypeScript, and Tailwind CSS that demonstrates CQRS and event sourcing patterns with real-time updates.

## Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│   React App     │────▶│  Quarkus API │────▶│  Kafka/Redpanda │
│   (Frontend)    │     │   (Backend)  │     │   (Event Bus)   │
└─────────────────┘     └──────────────┘     └─────────────────┘
        │                       │                      │
        │                       ▼                      ▼
        │              ┌──────────────┐      ┌─────────────────┐
        └─────────────▶│ Kafka Streams│◀─────│  Apache Flink   │
         WebSocket     │   (Cache)    │      │  (Processing)   │
                       └──────────────┘      └─────────────────┘
```

## Features

### Product Catalog
- **Dynamic Product Display**: Products loaded from Kafka Streams cache (CQRS pattern)
- **Real-time Inventory Updates**: WebSocket connection for live inventory changes
- **Categories**: Electronics, Fashion, Home & Garden, Sports, Books, Toys, Beauty, Food & Grocery

### Search & Filtering
- **Full-text Search**: Search across product names, descriptions, brands, and tags
- **Category Filtering**: Filter products by category with product counts
- **Price Range**: Adjustable min/max price filters
- **Sorting Options**:
  - Featured (default)
  - Price: Low to High
  - Price: High to Low
  - Highest Rated
  - Most Popular
  - Newest First

### Shopping Experience
- **Product Cards**: Display product image, name, price, rating, and inventory status
- **Shopping Cart**: Add items, update quantities, view total
- **Checkout Flow**: Complete orders with shipping information
- **Recommendations**: Personalized product suggestions

### Event Tracking
All user interactions are tracked and sent to Kafka for real-time analytics:
- Page views
- Product views
- Search queries
- Add to cart events
- Purchase events
- Category browsing

## Technical Stack

- **React 18**: Modern React with hooks and functional components
- **TypeScript**: Type-safe development
- **Tailwind CSS v3**: Utility-first CSS framework
- **Vite**: Fast build tool and dev server
- **Axios**: HTTP client for API communication
- **React Router**: Client-side routing

## Project Structure

```
kartshoppe-frontend/
├── src/
│   ├── components/        # Reusable UI components
│   │   ├── ProductCard.tsx
│   │   ├── CategorySelector.tsx
│   │   ├── ShoppingCart.tsx
│   │   └── Navigation.tsx
│   ├── pages/             # Page components
│   │   ├── HomePage.tsx
│   │   ├── ProductsPage.tsx
│   │   ├── ProductDetailPage.tsx
│   │   └── CartPage.tsx
│   ├── services/          # Business logic
│   │   ├── EventTracker.ts    # Event tracking service
│   │   └── WebSocketService.ts # Real-time updates
│   ├── styles/
│   │   └── index.css      # Global styles & Tailwind
│   └── App.tsx            # Main app component
├── public/                # Static assets
├── package.json
└── vite.config.ts         # Vite configuration
```

## Getting Started

### Prerequisites
- Node.js 18+ and npm
- Running Quarkus API backend (port 8080)
- Running Redpanda/Kafka cluster

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Environment Configuration

The app connects to:
- API: `http://localhost:8080/api`
- WebSocket: `ws://localhost:8080/ws`

To change these, update the proxy configuration in `vite.config.ts`.

## How It Works

### 1. Product Loading (CQRS Pattern)

Products are served from a Kafka Streams cache maintained by the Quarkus API:

```typescript
// Initial product fetch
const response = await axios.get('/api/ecommerce/products', { 
  params: { limit: 200 }
})
```

### 2. Client-Side Filtering

All filtering happens on the frontend for instant response:

```typescript
// Apply filters to cached products
let filtered = [...allProducts]
filtered = filtered.filter(p => p.category === selectedCategory)
filtered = filtered.filter(p => p.price >= priceRange.min)
filtered.sort((a, b) => a.price - b.price)
```

### 3. Event Tracking

User interactions are tracked and sent to Kafka:

```typescript
EventTracker.trackEvent('PRODUCT_VIEW', {
  productId: product.productId,
  productName: product.name,
  category: product.category,
  price: product.price
})
```

### 4. Real-Time Updates

WebSocket connection receives inventory updates:

```typescript
ws.onmessage = (event) => {
  const update = JSON.parse(event.data)
  if (update.eventType === 'INVENTORY_UPDATE') {
    updateProductInventory(update.payload)
  }
}
```

## Color Scheme

Based on Ververica branding:
- **Primary**: Deep Navy (`#120a3d`)
- **Secondary**: Teal (`#05b89c`)
- **Accent**: Orange (`#ff6b35`)

## Performance Optimizations

- **Lazy Loading**: Products load with staggered animation
- **Debounced Search**: Search input is debounced to reduce API calls
- **Virtual Scrolling**: (Can be added for large product lists)
- **Memoization**: Product cards are memoized to prevent unnecessary re-renders
- **Client-Side Cache**: Products cached in state to minimize API requests

## Development Tips

1. **Hot Module Replacement**: Vite provides instant updates during development
2. **TypeScript Checking**: Run `npm run type-check` to verify types
3. **Linting**: Use `npm run lint` to check code quality
4. **Mock Data**: The app falls back to mock data if API is unavailable

## Deployment

```bash
# Build the production bundle
npm run build

# The output will be in dist/
# Can be served with any static file server
npx serve dist
```

## Contributing

1. Follow the existing code style
2. Add TypeScript types for all new code
3. Test filtering and sorting functionality
4. Ensure responsive design works on mobile
5. Track new user interactions with EventTracker

## License

Part of the Ververica Visual Demo project.