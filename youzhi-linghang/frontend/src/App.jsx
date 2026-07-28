import { useEffect, useState } from "react";
import { Layout } from "./components/Layout";
import { AboutPage } from "./pages/AboutPage";
import { CommunityPage } from "./pages/CommunityPage";
import { ContentPage } from "./pages/ContentPage";
import { HomePage } from "./pages/HomePage";
import { ProfilePage } from "./pages/ProfilePage";
import { TutorsPage } from "./pages/TutorsPage";

const routes = {
  "/": HomePage,
  "/home": HomePage,
  "/content": ContentPage,
  "/tutors": TutorsPage,
  "/community": CommunityPage,
  "/profile": ProfilePage,
  "/about": AboutPage,
};

function currentRoute() {
  const value = window.location.hash.replace(/^#/, "") || "/home";
  return routes[value] ? value : "/home";
}

export function App() {
  const [route, setRoute] = useState(currentRoute);

  useEffect(() => {
    const handleHashChange = () => setRoute(currentRoute());
    window.addEventListener("hashchange", handleHashChange);
    if (!window.location.hash) {
      window.location.hash = "#/home";
    }
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  const Page = routes[route] || HomePage;

  return (
    <Layout route={route}>
      <Page />
    </Layout>
  );
}
