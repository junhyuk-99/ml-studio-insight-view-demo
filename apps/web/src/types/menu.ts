export type MenuProgram = {
  pgmcode: string;
  pgmname: string;
  pgmpath: string;
  sortno: number | null;
};

export type MenuSub = {
  subcode: string;
  subname: string;
  sortno: number | null;
  programs: MenuProgram[];
};

export type MenuMid = {
  midcode: string;
  midname: string;
  sortno: number | null;
  programs: MenuProgram[];
  submenus: MenuSub[];
};

export type MenuResponse = {
  menus: MenuMid[];
};

