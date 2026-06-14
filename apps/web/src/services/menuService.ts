import { apiClient } from './apiClient';
import type { UserRole } from '../types/auth';
import type { MenuMid, MenuResponse } from '../types/menu';

const DEMO_MENUS: MenuMid[] = [
  {
    midcode: 'DEMO-MENU-HOME',
    midname: 'Demo Dashboard',
    sortno: 10,
    programs: [
      {
        pgmcode: 'DEMO-PGM-HOME',
        pgmname: 'Home',
        pgmpath: '/',
        sortno: 10,
      },
    ],
    submenus: [],
  },
  {
    midcode: 'DEMO-MENU-AI',
    midname: 'AI Analysis',
    sortno: 20,
    programs: [],
    submenus: [
      {
        subcode: 'DEMO-SUB-AI-RESULTS',
        subname: 'Demo Results',
        sortno: 10,
        programs: [
          {
            pgmcode: 'DEMO-PGM-AI-OVERVIEW',
            pgmname: 'AI Overview',
            pgmpath: '/ai/overview',
            sortno: 10,
          },
          {
            pgmcode: 'DEMO-PGM-ANOMALY',
            pgmname: 'Anomaly Detection',
            pgmpath: '/ai/anomaly',
            sortno: 20,
          },
          {
            pgmcode: 'DEMO-PGM-THRESHOLD',
            pgmname: 'Threshold Alert',
            pgmpath: '/ai/threshold-alert',
            sortno: 30,
          },
          {
            pgmcode: 'DEMO-PGM-SUPERVISED',
            pgmname: 'Supervised Result',
            pgmpath: '/ai/supervised-result',
            sortno: 40,
          },
        ],
      },
    ],
  },
  {
    midcode: 'DEMO-MENU-AI-WORKFLOW',
    midname: 'AI Workflow',
    sortno: 25,
    programs: [],
    submenus: [
      {
        subcode: 'DEMO-SUB-AI-WORKFLOW',
        subname: 'Demo Pipeline',
        sortno: 10,
        programs: [
          {
            pgmcode: 'DEMO-PGM-PREPROCESS',
            pgmname: 'Preprocess / Feature',
            pgmpath: '/operation/preprocess',
            sortno: 10,
          },
          {
            pgmcode: 'DEMO-PGM-ALGORITHM',
            pgmname: 'Algorithm Selection',
            pgmpath: '/operation/algorithm',
            sortno: 20,
          },
          {
            pgmcode: 'DEMO-PGM-MODELTRAIN',
            pgmname: 'Model Training',
            pgmpath: '/operation/modeltrain',
            sortno: 30,
          },
        ],
      },
    ],
  },
  {
    midcode: 'DEMO-MENU-DATA',
    midname: 'Data Exploration',
    sortno: 30,
    programs: [
      {
        pgmcode: 'DEMO-PGM-TIMESERIES',
        pgmname: 'Timeseries',
        pgmpath: '/data-exploration/timeseries',
        sortno: 10,
      },
    ],
    submenus: [],
  },
];

function cloneDemoMenus(): MenuMid[] {
  return DEMO_MENUS.map((menu) => ({
    ...menu,
    programs: menu.programs.map((program) => ({ ...program })),
    submenus: menu.submenus.map((submenu) => ({
      ...submenu,
      programs: submenu.programs.map((program) => ({ ...program })),
    })),
  }));
}

function mergeWorkflowMenu(menus: MenuMid[]): MenuMid[] {
  const clonedMenus = menus.map((menu) => ({
    ...menu,
    programs: menu.programs.map((program) => ({ ...program })),
    submenus: menu.submenus.map((submenu) => ({
      ...submenu,
      programs: submenu.programs.map((program) => ({ ...program })),
    })),
  }));

  const workflowMenu = cloneDemoMenus().find((menu) => menu.midcode === 'DEMO-MENU-AI-WORKFLOW');
  if (!workflowMenu) {
    return clonedMenus;
  }

  const existingPaths = new Set<string>();
  clonedMenus.forEach((menu) => {
    menu.programs.forEach((program) => existingPaths.add(program.pgmpath));
    menu.submenus.forEach((submenu) => {
      submenu.programs.forEach((program) => existingPaths.add(program.pgmpath));
    });
  });

  const workflowPaths = workflowMenu.submenus.flatMap((submenu) => submenu.programs.map((program) => program.pgmpath));
  if (workflowPaths.every((path) => existingPaths.has(path))) {
    return clonedMenus;
  }

  return [...clonedMenus, workflowMenu];
}

function hasMenuItems(response: MenuResponse): boolean {
  return response.menus.some((menu) => menu.programs.length > 0 || menu.submenus.some((submenu) => submenu.programs.length > 0));
}

export const menuService = {
  async getMenu(role: UserRole) {
    const encodedRole = encodeURIComponent(role);

    try {
      const response = await apiClient.request<MenuResponse>(`/api/menu?role=${encodedRole}`);
      if (hasMenuItems(response)) {
        return {
          menus: mergeWorkflowMenu(response.menus),
        };
      }

      return this.getDemoMenu();
    } catch (error) {
      console.warn('Using static demo navigation fallback.', error);
      return this.getDemoMenu();
    }
  },

  getDemoMenu(): MenuResponse {
    return {
      menus: mergeWorkflowMenu(cloneDemoMenus()),
    };
  },
};

