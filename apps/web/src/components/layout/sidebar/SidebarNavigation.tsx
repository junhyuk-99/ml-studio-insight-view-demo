import { List } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import type { SidebarMenuSection } from '../../../config/navigationConfig';
import { SidebarSection } from './SidebarSection';

type SidebarNavigationProps = {
  sections: SidebarMenuSection[];
  activePath: string;
  onNavigate: (path: string) => void;
};

const SECTION_STORAGE_KEY = 'sidebar-expanded-sections';
const SUBMENU_STORAGE_KEY = 'sidebar-expanded-submenus';

function loadStoredState(key: string): Record<string, boolean> {
  try {
    const storedValue = localStorage.getItem(key);
    if (!storedValue) {
      return {};
    }

    const parsed = JSON.parse(storedValue);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

export function SidebarNavigation({ sections, activePath, onNavigate }: SidebarNavigationProps) {
  const [expandedBySectionId, setExpandedBySectionId] = useState<Record<string, boolean>>(() =>
    loadStoredState(SECTION_STORAGE_KEY),
  );
  const [expandedBySubmenuId, setExpandedBySubmenuId] = useState<Record<string, boolean>>(() =>
    loadStoredState(SUBMENU_STORAGE_KEY),
  );

  const activeMenuIds = useMemo(() => {
    for (const section of sections) {
      for (const submenu of section.submenus) {
        const hasActivePath = submenu.items.some((item) => item.path === activePath);
        if (hasActivePath) {
          return {
            sectionId: section.id,
            submenuId: submenu.id,
          };
        }
      }
    }

    return {
      sectionId: null,
      submenuId: null,
    };
  }, [sections, activePath]);

  useEffect(() => {
    setExpandedBySectionId((previousState) => {
      const nextState: Record<string, boolean> = {};

      for (const section of sections) {
        nextState[section.id] = previousState[section.id] ?? false;
      }

      if (activeMenuIds.sectionId) {
        nextState[activeMenuIds.sectionId] = true;
      }

      return nextState;
    });

    setExpandedBySubmenuId((previousState) => {
      const nextState: Record<string, boolean> = {};

      for (const section of sections) {
        for (const submenu of section.submenus) {
          nextState[submenu.id] = previousState[submenu.id] ?? false;
        }
      }

      if (activeMenuIds.submenuId) {
        nextState[activeMenuIds.submenuId] = true;
      }

      return nextState;
    });
  }, [sections, activeMenuIds]);

  useEffect(() => {
    localStorage.setItem(SECTION_STORAGE_KEY, JSON.stringify(expandedBySectionId));
  }, [expandedBySectionId]);

  useEffect(() => {
    localStorage.setItem(SUBMENU_STORAGE_KEY, JSON.stringify(expandedBySubmenuId));
  }, [expandedBySubmenuId]);

  return (
    <List disablePadding>
      {sections.map((section) => (
        <SidebarSection
          key={section.id}
          section={section}
          expanded={expandedBySectionId[section.id] ?? false}
          expandedBySubmenuId={expandedBySubmenuId}
          activePath={activePath}
          onToggleSection={(sectionId) => {
            setExpandedBySectionId((previousState) => ({
              ...previousState,
              [sectionId]: !(previousState[sectionId] ?? false),
            }));
          }}
          onToggleSubmenu={(submenuId) => {
            setExpandedBySubmenuId((previousState) => ({
              ...previousState,
              [submenuId]: !(previousState[submenuId] ?? false),
            }));
          }}
          onNavigate={onNavigate}
        />
      ))}
    </List>
  );
}