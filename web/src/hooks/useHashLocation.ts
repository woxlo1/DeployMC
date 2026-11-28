import { useEffect, useState } from 'react';
import { defaultRoute, parseHashLocation, type HashLocation } from '../app/routes';

export function useHashLocation(): HashLocation {
  const [location, setLocation] = useState<HashLocation>(() => {
    const parsed = parseHashLocation(window.location.hash);
    return parsed.route === null ? parsed : { route: parsed.route, state: parsed.state };
  });

  useEffect(() => {
    const onHashChange = () => setLocation(parseHashLocation(window.location.hash));
    window.addEventListener('hashchange', onHashChange);
    if (window.location.hash === '') {
      window.location.hash = `#/${defaultRoute().path}`;
    }
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  return location;
}
