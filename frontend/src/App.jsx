import React from 'react';
import { AppProvider, useApp } from './context/AppContext';
import SearchScreen from './screens/SearchScreen';
import LoadingScreen from './screens/LoadingScreen';
import DashboardScreen from './screens/DashboardScreen';
import ErrorScreen from './screens/ErrorScreen';

function AppRouter() {
  const { screen } = useApp();

  switch (screen) {
    case 'loading':    return <LoadingScreen />;
    case 'dashboard':  return <DashboardScreen />;
    case 'error':      return <ErrorScreen />;
    default:           return <SearchScreen />;
  }
}

export default function App() {
  return (
    <AppProvider>
      <AppRouter />
    </AppProvider>
  );
}
