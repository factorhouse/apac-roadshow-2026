// Global type declarations

declare global {
  interface Window {
    ProductService?: {
      handleProductUpdate: (product: any) => void
      updateProductsFromCache: (products: any[]) => void
    }
  }
}

export {}
