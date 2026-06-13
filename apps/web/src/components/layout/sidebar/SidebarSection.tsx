import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded';
import SubdirectoryArrowRightRoundedIcon from '@mui/icons-material/SubdirectoryArrowRightRounded';
import {
  Box,
  Collapse,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import type { SidebarMenuSection } from '../../../config/navigationConfig';

type SidebarSectionProps = {
  section: SidebarMenuSection;
  expanded: boolean;
  expandedBySubmenuId: Record<string, boolean>;
  activePath: string;
  onToggleSection: (sectionId: string) => void;
  onToggleSubmenu: (submenuId: string) => void;
  onNavigate: (path: string) => void;
};

export function SidebarSection({
  section,
  expanded,
  expandedBySubmenuId,
  activePath,
  onToggleSection,
  onToggleSubmenu,
  onNavigate,
}: SidebarSectionProps) {
  return (
    <Box sx={{ mb: 1.4 }}>
      
      <ListItemButton
        onClick={() => onToggleSection(section.id)}
        sx={{
          borderRadius: 1.8,
          px: 1.1,
          py: 0.7,
          bgcolor: expanded ? 'rgba(255, 255, 255, 0.14)' : 'rgba(255, 255, 255, 0.05)',
        }}
      >
        <ListItemText
          primary={section.title}
          primaryTypographyProps={{
            fontSize: 14,
            fontWeight: 900,
            color: '#f3f7ff',
          }}
        />

        <Box sx={{ color: '#bed1f7' }}>
          {expanded ? <ExpandMoreRoundedIcon fontSize="small" /> : <ChevronRightRoundedIcon fontSize="small" />}
        </Box>
      </ListItemButton>

      <Collapse in={expanded} timeout="auto" unmountOnExit>
        <List disablePadding sx={{ mt: 0.8, pl: 0.6 }}>
          {section.submenus.map((submenu) => {
            const submenuExpanded = expandedBySubmenuId[submenu.id] ?? true;

            return (
              <Box key={submenu.id} sx={{ mb: 0.9 }}>
                
                <ListItemButton
                  onClick={() => onToggleSubmenu(submenu.id)}
                  sx={{
                    borderRadius: 1.4,
                    px: 1.2,
                    py: 0.55,
                    minHeight: 34,
                    bgcolor: submenuExpanded ? 'rgba(255, 255, 255, 0.1)' : 'transparent',
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 24, color: '#bdd1f7' }}>
                    <SubdirectoryArrowRightRoundedIcon sx={{ fontSize: 18 }} />
                  </ListItemIcon>

                  <ListItemText
                    primary={submenu.title}
                    primaryTypographyProps={{ fontSize: 12, fontWeight: 700, color: '#dce8ff' }}
                  />

                  <Box sx={{ color: '#bed1f7' }}>
                    {submenuExpanded ? (
                      <ExpandMoreRoundedIcon sx={{ fontSize: 18 }} />
                    ) : (
                      <ChevronRightRoundedIcon sx={{ fontSize: 18 }} />
                    )}
                  </Box>
                </ListItemButton>

                <Collapse in={submenuExpanded} timeout="auto" unmountOnExit>
                  <List disablePadding sx={{ pt: 0.35 }}>
                    {submenu.items.map((item) => {
                      const ItemIcon = item.icon;
                      const selected = activePath === item.path;

                      return (
                        <ListItemButton
                          key={item.path}
                          selected={selected}
                          onClick={() => onNavigate(item.path)}
                          sx={{
                            ml: 0.8,
                            mb: 0.25,
                            pl: 1.9,
                            pr: 1,
                            py: 0.3,
                            borderRadius: 1.4,
                            minHeight: 38,
                            '&.Mui-selected': {
                              bgcolor: 'rgba(39, 109, 255, 0.38)',
                              color: '#ffffff',
                            },
                          }}
                        >
                          <ListItemIcon sx={{ minWidth: 24, color: selected ? '#ffffff' : '#bdd0f6' }}>
                            <ItemIcon sx={{ fontSize: 16 }} />
                          </ListItemIcon>

                          <ListItemText
                            primary={item.title}
                            secondary={item.pathLabel}
                            primaryTypographyProps={{
                              fontSize: 12.5,
                              fontWeight: selected ? 800 : 600,
                              color: selected ? '#ffffff' : '#dce8ff',
                              noWrap: true,
                            }}
                            secondaryTypographyProps={{
                              fontSize: 10,
                              color: selected ? '#e7f0ff' : 'rgba(220, 232, 255, 0.72)',
                              noWrap: true,
                            }}
                          />
                        </ListItemButton>
                      );
                    })}
                  </List>
                </Collapse>
              </Box>
            );
          })}
        </List>
      </Collapse>

      <Divider sx={{ mt: 1.1, borderColor: 'rgba(197, 216, 255, 0.18)' }} />
    </Box>
  );
}
