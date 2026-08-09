import { BrowserRouter, Route, Routes } from 'react-router-dom';

/**
 * Route skeleton for the screens in docs/IMPLEMENTATION_PLAN.md.
 * Screens are filled in phase by phase; every element below is a placeholder
 * until its feature module lands.
 */
export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* public */}
        <Route path="/" element={<Placeholder name="Landing" />} />
        <Route path="/register" element={<Placeholder name="Registration" />} />
        <Route path="/register/pending" element={<Placeholder name="Pending Approval" />} />
        <Route path="/login" element={<Placeholder name="Login (Password / OTP tabs)" />} />
        <Route path="/forgot-password" element={<Placeholder name="Forgot Password" />} />
        <Route path="/restricted" element={<Placeholder name="Access Restricted" />} />

        {/* any active user */}
        <Route path="/home" element={<Placeholder name="Home" />} />
        <Route path="/departments" element={<Placeholder name="Department List" />} />
        <Route path="/departments/:departmentId" element={<Placeholder name="Folders" />} />
        <Route path="/folders/:folderId" element={<Placeholder name="Folder Contents" />} />
        <Route path="/files/:fileId" element={<Placeholder name="File Preview" />} />
        <Route path="/upload" element={<Placeholder name="Upload" />} />
        <Route path="/my-uploads" element={<Placeholder name="My Uploads" />} />
        <Route path="/downloads" element={<Placeholder name="Downloads" />} />
        <Route path="/favorites" element={<Placeholder name="Favorites" />} />
        <Route path="/search" element={<Placeholder name="Search Results" />} />
        <Route path="/notifications" element={<Placeholder name="Notifications" />} />
        <Route path="/profile" element={<Placeholder name="My Profile" />} />

        {/* admin */}
        <Route path="/admin" element={<Placeholder name="Admin Dashboard" />} />
        <Route path="/admin/requests" element={<Placeholder name="Registration Requests" />} />
        <Route path="/admin/members" element={<Placeholder name="Members" />} />
        <Route path="/admin/members/:memberId" element={<Placeholder name="Member Activity" />} />
        <Route path="/admin/deletions" element={<Placeholder name="Deletions Log" />} />
        <Route path="/admin/departments" element={<Placeholder name="Department Management" />} />
        <Route path="/admin/folders" element={<Placeholder name="Folder Management" />} />
        <Route path="/admin/files" element={<Placeholder name="File Management" />} />
        <Route path="/admin/reports" element={<Placeholder name="Reports" />} />
        <Route path="/admin/logs" element={<Placeholder name="Activity Logs" />} />
        <Route path="/admin/settings" element={<Placeholder name="Settings" />} />

        {/* static */}
        <Route path="/help" element={<Placeholder name="Help & Support" />} />
        <Route path="/about" element={<Placeholder name="About" />} />
        <Route path="/privacy" element={<Placeholder name="Privacy Policy" />} />

        <Route path="*" element={<Placeholder name="Not Found" />} />
      </Routes>
    </BrowserRouter>
  );
}

function Placeholder({ name }: { name: string }) {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="rounded-lg border border-slate-200 bg-white px-8 py-6 text-center shadow-sm">
        <p className="text-sm uppercase tracking-wide text-navy-500">ALLGOSANDCOURTCOPYS</p>
        <h1 className="mt-2 text-2xl font-semibold text-navy-800">{name}</h1>
        <p className="mt-2 text-sm text-slate-500">Not implemented yet.</p>
      </div>
    </main>
  );
}
