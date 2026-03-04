'use client'

import { useEffect, useRef } from 'react'

export default function Home() {
  const iframeRef = useRef<HTMLIFrameElement>(null)

  useEffect(() => {
    // Handle resize
    const handleResize = () => {
      if (iframeRef.current) {
        iframeRef.current.style.height = `${window.innerHeight}px`
      }
    }
    handleResize()
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return (
    <iframe
      ref={iframeRef}
      src="/pkpd-simulator.html"
      style={{
        width: '100%',
        height: '100vh',
        border: 'none',
        display: 'block'
      }}
      title="PK/PD Monte Carlo Simulator"
    />
  )
}
