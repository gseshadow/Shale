import React from 'react';
import ReactDOM from 'react-dom/client';
import App, { createAppRouter } from './App';

const router = createAppRouter();

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App router={router} />
  </React.StrictMode>,
);
