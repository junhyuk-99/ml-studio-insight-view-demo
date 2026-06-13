import type { MenuMid } from '../types/menu';

export type MainLayoutOutletContext = {
  menus: MenuMid[];
  menuLoading: boolean;
  menuError: string | null;
};
