import React, { useState } from "react";
import { Link } from "react-router-dom";
import { EventTracker } from "../services/EventTracker";
import { Product } from "../services/ProductService";
import ImageCacheService from "../services/ImageCacheService";

interface ProductCardProps {
  product: Product;
}

const ProductCard: React.FC<ProductCardProps> = ({ product }) => {
  const [imageLoaded, setImageLoaded] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const handleProductClick = () => {
    EventTracker.trackProductView(
      product.productId,
      product.name,
      product.price,
    );
  };

  return (
    <Link
      to={`/product/${product.productId}`}
      onClick={handleProductClick}
      className="product-card block transform transition-all duration-300 hover:-translate-y-1"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div className="relative h-64 overflow-hidden bg-gradient-to-br from-gray-100 to-gray-200">
        {!imageLoaded && (
          <div className="absolute inset-0 animate-pulse bg-gradient-to-br from-gray-200 to-gray-300" />
        )}
        <img
          src={ImageCacheService.getImageUrl(
            product.productId,
            product.imageUrl,
            product.category,
          )}
          alt={product.name}
          onLoad={() => setImageLoaded(true)}
          onError={(e) =>
            ImageCacheService.handleImageError(
              e,
              product.productId,
              product.category,
            )
          }
          className={`w-full h-full object-cover transition-all duration-700 ${
            imageLoaded ? "opacity-100" : "opacity-0"
          } ${isHovered ? "scale-110" : "scale-100"}`}
        />

        {/* Badges */}
        <div className="absolute top-2 left-2 flex flex-col gap-2">
          {product.isNew && (
            <div className="badge bg-green-500 text-white animate-pulse-subtle">
              NEW
            </div>
          )}
          {product.isSale && product.discount && (
            <div className="badge bg-red-500 text-white">
              -{product.discount}%
            </div>
          )}
          {product.inventory < 10 && product.inventory > 0 && (
            <div className="badge bg-yellow-100 text-yellow-800">
              Only {product.inventory} left!
            </div>
          )}
          {product.inventory === 0 && (
            <div className="badge bg-red-100 text-red-800">Out of Stock</div>
          )}
        </div>

        <div className="absolute top-2 right-2 badge bg-white/90 backdrop-blur-sm text-gray-700">
          {product.category}
        </div>

        {/* Quick Add Button - Shows on Hover */}
        {/* <div className={`absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-4 transform transition-all duration-300 ${
          isHovered ? 'translate-y-0 opacity-100' : 'translate-y-full opacity-0'
        }`}>
          <button
            onClick={handleAddToCart}
            disabled={product.inventory === 0}
            className="w-full py-2 px-4 bg-white text-gray-900 font-semibold rounded-lg hover:bg-gray-100 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            Quick Add to Cart
          </button>
        </div> */}
      </div>

      <div className="p-4">
        {product.brand && (
          <p className="text-xs text-gray-500 uppercase tracking-wide mb-1">
            {product.brand}
          </p>
        )}

        <h3 className="text-lg font-semibold text-gray-800 mb-1 line-clamp-1">
          {product.name}
        </h3>

        <p className="text-sm text-gray-600 mb-3 line-clamp-2">
          {product.description}
        </p>

        <div className="flex items-center mb-3">
          <div className="flex items-center">
            {[...Array(5)].map((_, i) => (
              <svg
                key={i}
                className={`w-4 h-4 ${i < Math.floor(product.rating) ? "text-yellow-400" : "text-gray-300"}`}
                fill="currentColor"
                viewBox="0 0 20 20"
              >
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
            ))}
            <span className="ml-1 text-sm text-gray-600">
              {product.rating.toFixed(1)} ({product.reviewCount})
            </span>
          </div>
        </div>

        <div className="flex items-center justify-between">
          <div>
            {product.isSale && product.originalPrice ? (
              <div>
                <span className="text-sm text-gray-500 line-through">
                  ${product.originalPrice.toFixed(2)}
                </span>
                <span className="text-2xl font-bold text-red-600 ml-2">
                  ${product.price.toFixed(2)}
                </span>
              </div>
            ) : (
              <span className="text-2xl font-bold text-gray-900">
                ${product.price.toFixed(2)}
              </span>
            )}
          </div>

          <div
            className={`${
              product.inventory === 0
                ? "bg-gray-100 text-gray-500 border border-gray-200"
                : "bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700 text-white shadow-md hover:shadow-lg hover:scale-105"
            } px-4 py-2 rounded-lg text-sm font-semibold transform transition-all duration-200 flex items-center justify-center`}
          >
            {/* Changed icon to an Arrow/Eye style to indicate 'View' instead of 'Add' */}
            <svg
              className="w-4 h-4 inline mr-1"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
              />
            </svg>
            {product.inventory === 0 ? "Out of Stock" : "View Details"}
          </div>
        </div>
      </div>
    </Link>
  );
};

export default ProductCard;
