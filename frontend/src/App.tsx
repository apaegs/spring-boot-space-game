import { Route, Routes } from 'react-router-dom'
import { HealthIndicator } from './components/HealthIndicator'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Game } from './pages/Game'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { SelectedShipProvider } from './ship/SelectedShipProvider'
import './App.css'

function App() {
    return (
        <>
            <HealthIndicator />
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route element={<ProtectedRoute />}>
                    <Route
                        path="/"
                        element={
                            // SelectedShipProvider sits inside the protected route so it
                            // only mounts after we know there's a user, and its initial
                            // /api/ships fetch authenticates correctly.
                            <SelectedShipProvider>
                                <Game />
                            </SelectedShipProvider>
                        }
                    />
                </Route>
            </Routes>
        </>
    )
}

export default App
