import React from 'react';
import Movies from './Movies';

// Series is the same layout as Movies, but filtered for type='series'
export default function Series() {
  return <Movies contentType="series" />;
}
