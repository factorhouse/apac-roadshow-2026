import React from 'react'
import { EventTracker } from '../services/EventTracker'

interface CategorySelectorProps {
  selectedCategory: string | null
  onCategorySelect: (category: string | null) => void
}

const categories = [
  { id: 'all', name: 'All Products', icon: '🛍️', color: 'from-ververica-purple to-primary-600' },
  { id: 'Electronics', name: 'Electronics', icon: '💻', color: 'from-blue-500 to-blue-600' },
  { id: 'Fashion', name: 'Fashion', icon: '👕', color: 'from-pink-500 to-pink-600' },
  { id: 'Home & Garden', name: 'Home & Garden', icon: '🏠', color: 'from-green-500 to-green-600' },
  { id: 'Sports', name: 'Sports & Fitness', icon: '⚽', color: 'from-orange-500 to-orange-600' },
  { id: 'Books', name: 'Books', icon: '📚', color: 'from-indigo-500 to-indigo-600' },
  { id: 'Toys', name: 'Toys & Games', icon: '🎮', color: 'from-purple-500 to-purple-600' },
  { id: 'Beauty', name: 'Beauty & Health', icon: '💄', color: 'from-rose-500 to-rose-600' },
  { id: 'Food & Grocery', name: 'Food & Grocery', icon: '🍕', color: 'from-yellow-500 to-yellow-600' },
]

const CategorySelector: React.FC<CategorySelectorProps> = ({ selectedCategory, onCategorySelect }) => {
  const handleCategoryClick = (categoryId: string) => {
    const newCategory = categoryId === 'all' ? null : categoryId
    onCategorySelect(newCategory)
    
    // Track category browsing
    if (newCategory) {
      EventTracker.trackEvent('CATEGORY_BROWSE', {
        categoryId: newCategory,
        categoryName: categories.find(c => c.id === newCategory)?.name
      })
    }
  }

  return (
    <div className="bg-gradient-to-r from-ververica-navy via-primary-900 to-ververica-navy py-6">
      <div className="container mx-auto px-4">
        <div className="mb-4">
          <h2 className="text-xl font-bold text-white">Shop by Category</h2>
        </div>
        
        <div className="grid grid-cols-2 md:grid-cols-5 lg:grid-cols-9 gap-3">
          {categories.map((category) => {
            const isSelected = (category.id === 'all' && !selectedCategory) || 
                             (category.id === selectedCategory)
            
            return (
              <button
                key={category.id}
                onClick={() => handleCategoryClick(category.id)}
                className={`
                  relative group flex flex-col items-center justify-center p-4 rounded-xl
                  transition-all duration-300 transform hover:scale-105 active:scale-95
                  ${isSelected 
                    ? 'bg-gradient-to-br ' + category.color + ' text-white shadow-xl scale-105' 
                    : 'bg-white/10 backdrop-blur-sm text-white hover:bg-white/20'
                  }
                `}
              >
                {/* Glow effect for selected category */}
                {isSelected && (
                  <div className={`absolute inset-0 bg-gradient-to-br ${category.color} rounded-xl blur-xl opacity-50 -z-10`}></div>
                )}
                
                <span className="text-2xl mb-2">{category.icon}</span>
                <span className="text-xs font-semibold text-center line-clamp-2">
                  {category.name}
                </span>
                
                {/* Real-time indicator */}
                {isSelected && (
                  <div className="absolute -top-1 -right-1">
                    <div className="w-3 h-3 bg-ververica-bright-teal rounded-full animate-ping"></div>
                    <div className="absolute inset-0 w-3 h-3 bg-ververica-bright-teal rounded-full"></div>
                  </div>
                )}
              </button>
            )
          })}
        </div>

      </div>
    </div>
  )
}

export default CategorySelector