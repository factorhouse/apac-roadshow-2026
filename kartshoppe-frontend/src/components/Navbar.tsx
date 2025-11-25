import React from "react";
import { Link } from "react-router-dom";
import { useCart } from "../contexts/CartContext";
import { useWebSocket } from "../contexts/WebSocketContext";

const Navbar: React.FC = () => {
  const { itemCount } = useCart();
  const { connected } = useWebSocket();

  return (
    <nav className="bg-gradient-to-r from-ververica-navy via-primary-900 to-ververica-navy shadow-2xl sticky top-0 z-50">
      <div className="container mx-auto px-4">
        <div className="flex justify-between items-center h-16">
          <Link to="/" className="flex items-center space-x-3">
            <div className="w-10 h-10 bg-gradient-to-br from-ververica-teal to-ververica-bright-teal rounded-lg flex items-center justify-center shadow-lg">
              <svg
                className="w-6 h-6 text-white"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
                />
              </svg>
            </div>
            <div>
              <span className="text-2xl font-bold text-white">KartShoppe</span>
              <div className="text-xs text-ververica-bright-teal">
                APAC Roadshow 2026
              </div>
            </div>
          </Link>

          <div className="flex items-center space-x-6">
            <Link
              to="/products"
              className="text-white hover:text-ververica-bright-teal font-medium transition-colors"
            >
              Products
            </Link>

            <div className="flex items-center space-x-2">
              <div
                className={`w-2 h-2 rounded-full ${
                  connected ? "bg-ververica-bright-teal" : "bg-red-500"
                } animate-pulse`}
              />
              <span className="text-sm text-white/80">
                {connected ? "Live Stream" : "Offline"}
              </span>
            </div>

            <Link to="/cart" className="relative">
              <div className="p-2 hover:bg-white/10 rounded-lg transition-colors">
                <svg
                  className="w-6 h-6 text-white"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"
                  />
                </svg>
                {itemCount > 0 && (
                  <span className="absolute -top-1 -right-1 bg-ververica-bright-teal text-ververica-navy text-xs rounded-full w-5 h-5 flex items-center justify-center animate-pulse font-bold">
                    {itemCount}
                  </span>
                )}
              </div>
            </Link>

            <div className="flex items-center space-x-2 px-3 py-1 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20">
              <svg
                className="w-5 h-5 text-white"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                />
              </svg>
              <span className="text-sm font-medium text-white">Guest</span>
            </div>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
