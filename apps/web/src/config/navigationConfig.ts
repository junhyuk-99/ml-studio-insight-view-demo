import AccountTreeRoundedIcon from '@mui/icons-material/AccountTreeRounded';
import BarChartRoundedIcon from '@mui/icons-material/BarChartRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import DataObjectRoundedIcon from '@mui/icons-material/DataObjectRounded';
import GridOnRoundedIcon from '@mui/icons-material/GridOnRounded';
import HomeRoundedIcon from '@mui/icons-material/HomeRounded';
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded';
import ModelTrainingRoundedIcon from '@mui/icons-material/ModelTrainingRounded';
import MonitorHeartRoundedIcon from '@mui/icons-material/MonitorHeartRounded';
import ScienceRoundedIcon from '@mui/icons-material/ScienceRounded';
import ShowChartRoundedIcon from '@mui/icons-material/ShowChartRounded';
import TimelineRoundedIcon from '@mui/icons-material/TimelineRounded';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import type { SvgIconComponent } from '@mui/icons-material';
import type { MenuMid, MenuProgram, MenuSub } from '../types/menu';

export type AppRouteId =
  | 'home'
  | 'data-exploration-histogram'
  | 'data-exploration-timeseries'
  | 'data-exploration-correlation-heatmap'
  | 'data-exploration-boxplot'
  | 'data-exploration-processflow'
  | 'operation-preprocess'
  | 'operation-testpreprocess'
  | 'operation-user'
  | 'operation-algorithm'
  | 'operation-modeltrain'
  | 'ai-anomaly'
  | 'ai-health-index'
  | 'ai-anomaly-cause'
  | 'ai-threshold-alert'
  | 'ai-supervised-result'
  | 'ai-overview';

export type AppRouteMeta = {
  id: AppRouteId;
  path: string;
  title: string;
  description: string;
  order: number;
  icon: SvgIconComponent;
};

export type SidebarLeafItem = {
  id: string;
  title: string;
  path: string;
  pathLabel: string;
  description: string;
  icon: SvgIconComponent;
  order: number;
};

export type SidebarSubmenuSection = {
  id: string;
  title: string;
  order: number;
  items: SidebarLeafItem[];
};

export type SidebarMenuSection = {
  id: string;
  title: string;
  order: number;
  submenus: SidebarSubmenuSection[];
};

const DIRECT_PROGRAM_SUBMENU_TITLE = '기본 메뉴';

export const APP_ROUTE_META: readonly AppRouteMeta[] = [
  {
    id: 'home',
    path: '/',
    title: '홈',
    description: '시스템 현황과 주요 분석 요약을 확인합니다.',
    order: 0,
    icon: HomeRoundedIcon,
  },
  {
    id: 'data-exploration-histogram',
    path: '/data-exploration/histogram',
    title: '히스토그램',
    description: '원천 데이터 분포를 확인합니다.',
    order: 10,
    icon: BarChartRoundedIcon,
  },
  {
    id: 'data-exploration-timeseries',
    path: '/data-exploration/timeseries',
    title: '시계열 추이',
    description: '시간 흐름에 따른 센서 변화를 확인합니다.',
    order: 20,
    icon: TimelineRoundedIcon,
  },
  {
    id: 'data-exploration-correlation-heatmap',
    path: '/data-exploration/correlation-heatmap',
    title: '상관관계 히트맵',
    description: '변수 간 상관관계를 확인합니다.',
    order: 30,
    icon: GridOnRoundedIcon,
  },
  {
    id: 'data-exploration-boxplot',
    path: '/data-exploration/boxplot',
    title: '박스플롯',
    description: '변수별 분포와 이상치를 비교합니다.',
    order: 40,
    icon: ShowChartRoundedIcon,
  },
  {
    id: 'data-exploration-processflow',
    path: '/data-exploration/processflow',
    title: '공정 흐름 분석',
    description: '공정 상태 구간과 온도 PV/SV 추이를 함께 확인합니다.',
    order: 50,
    icon: AccountTreeRoundedIcon,
  },
  {
    id: 'operation-preprocess',
    path: '/operation/preprocess',
    title: 'AI 데이터 전처리',
    description: 'raw 데이터와 feature 생성 정책을 관리합니다.',
    order: 10,
    icon: DataObjectRoundedIcon,
  },
  {
    id: 'operation-testpreprocess',
    path: '/operation/testpreprocess',
    title: '테스트 전처리',
    description: 'feature 자동 생성 정책을 테스트합니다.',
    order: 20,
    icon: ScienceRoundedIcon,
  },
  {
    id: 'operation-user',
    path: '/operation/user',
    title: '사원 정보 관리',
    description: '사원 계정과 권한 정보를 관리합니다.',
    order: 30,
    icon: ManageAccountsRoundedIcon,
  },
  {
    id: 'operation-algorithm',
    path: '/operation/algorithm',
    title: '알고리즘 선택',
    description: '분석 목적에 맞는 알고리즘을 선택합니다.',
    order: 10,
    icon: AccountTreeRoundedIcon,
  },
  {
    id: 'operation-modeltrain',
    path: '/operation/modeltrain',
    title: 'AI 모델 학습',
    description: '학습 정책 설정과 실행 상태를 확인합니다.',
    order: 20,
    icon: ModelTrainingRoundedIcon,
  },
  {
    id: 'ai-anomaly',
    path: '/ai/anomaly',
    title: '설비 이상 탐지',
    description: '이상 탐지 결과를 조회합니다.',
    order: 10,
    icon: WarningAmberRoundedIcon,
  },
  {
    id: 'ai-health-index',
    path: '/ai/health-index',
    title: 'Health Index 산출',
    description: '이상탐지 결과 기반 Health Index 추이를 확인합니다.',
    order: 20,
    icon: MonitorHeartRoundedIcon,
  },
  {
    id: 'ai-anomaly-cause',
    path: '/ai/anomaly-cause',
    title: 'AI 원인 분석',
    description: '이상탐지 결과 기반 원인 후보 지표를 확인합니다.',
    order: 30,
    icon: WarningAmberRoundedIcon,
  },
  {
    id: 'ai-threshold-alert',
    path: '/ai/threshold-alert',
    title: '임계치 경고 알람',
    description: '설정된 임계치 초과 알람 이력을 확인합니다.',
    order: 40,
    icon: WarningAmberRoundedIcon,
  },
  {
    id: 'ai-supervised-result',
    path: '/ai/supervised-result',
    title: '지도학습 분석 결과',
    description: 'Random Forest 지도학습 성능과 예측 결과를 확인합니다.',
    order: 50,
    icon: ModelTrainingRoundedIcon,
  },
  {
    id: 'ai-overview',
    path: '/ai/overview',
    title: 'AI 운영 상태',
    description: '활성 모델과 최근 실행 상태를 확인합니다.',
    order: 10,
    icon: MonitorHeartRoundedIcon,
  },
] as const;

const ROUTE_META_BY_PATH = new Map<string, AppRouteMeta>(
  APP_ROUTE_META.map((routeMeta) => [routeMeta.path, routeMeta]),
);

const ROUTE_META_BY_ID = new Map<AppRouteId, AppRouteMeta>(
  APP_ROUTE_META.map((routeMeta) => [routeMeta.id, routeMeta]),
);

export function getRouteMetaByPath(path: string): AppRouteMeta | null {
  return ROUTE_META_BY_PATH.get(path) ?? null;
}

export function getRouteMetaById(routeId: AppRouteId): AppRouteMeta {
  const routeMeta = ROUTE_META_BY_ID.get(routeId);
  if (!routeMeta) {
    throw new Error(`Unknown route id: ${routeId}`);
  }
  return routeMeta;
}

export function getRoutePathById(routeId: AppRouteId): string {
  return getRouteMetaById(routeId).path;
}

function normalizePath(path: string | null | undefined): string | null {
  if (!path) {
    return null;
  }

  const trimmedPath = path.trim();
  if (!trimmedPath) {
    return null;
  }

  return trimmedPath.startsWith('/') ? trimmedPath : `/${trimmedPath}`;
}

function normalizeLabel(value: string | null | undefined, fallback: string): string {
  const trimmed = value?.trim();
  if (!trimmed) {
    return fallback;
  }
  return trimmed;
}

function normalizeSortNo(value: number | null | undefined): number {
  return value == null ? Number.MAX_SAFE_INTEGER : value;
}

function compareBySortAndTitle(
  leftSortNo: number | null | undefined,
  rightSortNo: number | null | undefined,
  leftTitle: string,
  rightTitle: string,
): number {
  const sortCompare = normalizeSortNo(leftSortNo) - normalizeSortNo(rightSortNo);
  if (sortCompare !== 0) {
    return sortCompare;
  }
  return leftTitle.localeCompare(rightTitle, 'ko-KR');
}

function sortMidMenus(menus: MenuMid[]): MenuMid[] {
  return [...menus].sort((left, right) =>
    compareBySortAndTitle(left.sortno, right.sortno, left.midname ?? '', right.midname ?? ''),
  );
}

function sortSubmenus(submenus: MenuSub[]): MenuSub[] {
  return [...submenus].sort((left, right) =>
    compareBySortAndTitle(left.sortno, right.sortno, left.subname ?? '', right.subname ?? ''),
  );
}

function sortPrograms(programs: MenuProgram[]): MenuProgram[] {
  return [...programs].sort((left, right) =>
    compareBySortAndTitle(left.sortno, right.sortno, left.pgmname ?? '', right.pgmname ?? ''),
  );
}

function buildProgramItems(programs: MenuProgram[], usedPaths: Set<string>): SidebarLeafItem[] {
  const items: SidebarLeafItem[] = [];

  for (const program of sortPrograms(programs)) {
    const normalizedPath = normalizePath(program.pgmpath);
    if (!normalizedPath) {
      continue;
    }

    if (usedPaths.has(normalizedPath)) {
      continue;
    }

    usedPaths.add(normalizedPath);

    const routeMeta = getRouteMetaByPath(normalizedPath);

    items.push({
      id: normalizeLabel(program.pgmcode, normalizedPath),
      title: normalizeLabel(program.pgmname, routeMeta?.title ?? normalizedPath),
      path: normalizedPath,
      pathLabel: normalizedPath,
      description: routeMeta?.description ?? normalizedPath,
      icon: routeMeta?.icon ?? ChevronRightRoundedIcon,
      order: routeMeta?.order ?? normalizeSortNo(program.sortno),
    });
  }

  return items;
}

export function buildSidebarMenuSections(menus: MenuMid[]): SidebarMenuSection[] {
  const usedPaths = new Set<string>();
  const sections: SidebarMenuSection[] = [];

  for (const midMenu of sortMidMenus(menus)) {
    const submenus: SidebarSubmenuSection[] = [];

    const directItems = buildProgramItems(midMenu.programs, usedPaths);
    if (directItems.length > 0) {
      submenus.push({
        id: `${midMenu.midcode}::__direct`,
        title: DIRECT_PROGRAM_SUBMENU_TITLE,
        order: -1,
        items: directItems,
      });
    }

    for (const submenu of sortSubmenus(midMenu.submenus)) {
      const submenuItems = buildProgramItems(submenu.programs, usedPaths);
      if (submenuItems.length === 0) {
        continue;
      }

      submenus.push({
        id: `${midMenu.midcode}:${submenu.subcode}`,
        title: normalizeLabel(submenu.subname, '중분류'),
        order: normalizeSortNo(submenu.sortno),
        items: submenuItems,
      });
    }

    if (submenus.length === 0) {
      continue;
    }

    submenus.sort((left, right) =>
      compareBySortAndTitle(left.order, right.order, left.title, right.title),
    );

    sections.push({
      id: normalizeLabel(midMenu.midcode, `mid-${sections.length + 1}`),
      title: normalizeLabel(midMenu.midname, '대분류'),
      order: normalizeSortNo(midMenu.sortno),
      submenus,
    });
  }

  return sections.sort((left, right) => compareBySortAndTitle(left.order, right.order, left.title, right.title));
}

export function extractAccessiblePaths(menus: MenuMid[]): Set<string> {
  const accessiblePaths = new Set<string>();

  for (const midMenu of menus) {
    for (const program of midMenu.programs) {
      const normalizedPath = normalizePath(program.pgmpath);
      if (normalizedPath) {
        accessiblePaths.add(normalizedPath);
      }
    }

    for (const submenu of midMenu.submenus) {
      for (const program of submenu.programs) {
        const normalizedPath = normalizePath(program.pgmpath);
        if (normalizedPath) {
          accessiblePaths.add(normalizedPath);
        }
      }
    }
  }

  return accessiblePaths;
}
