import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';

export type SelectedAlgorithm = {
  algoCd: string;
  algoNm: string;
};

type SelectedAlgorithmContextValue = {
  selectedAlgorithm: SelectedAlgorithm | null;
  setSelectedAlgorithm: (algorithm: SelectedAlgorithm) => void;
  clearSelectedAlgorithm: () => void;
};

const SelectedAlgorithmContext = createContext<SelectedAlgorithmContextValue | undefined>(undefined);

export function SelectedAlgorithmProvider({ children }: PropsWithChildren) {
  const [selectedAlgorithm, setSelectedAlgorithmState] = useState<SelectedAlgorithm | null>(null);

  const setSelectedAlgorithm = useCallback((algorithm: SelectedAlgorithm) => {
    setSelectedAlgorithmState({
      algoCd: algorithm.algoCd.trim(),
      algoNm: algorithm.algoNm.trim(),
    });
  }, []);

  const clearSelectedAlgorithm = useCallback(() => {
    setSelectedAlgorithmState(null);
  }, []);

  const value = useMemo<SelectedAlgorithmContextValue>(() => ({
    selectedAlgorithm,
    setSelectedAlgorithm,
    clearSelectedAlgorithm,
  }), [clearSelectedAlgorithm, selectedAlgorithm, setSelectedAlgorithm]);

  return (
    <SelectedAlgorithmContext.Provider value={value}>
      {children}
    </SelectedAlgorithmContext.Provider>
  );
}

export function useSelectedAlgorithm() {
  const context = useContext(SelectedAlgorithmContext);
  if (!context) {
    throw new Error('useSelectedAlgorithm must be used inside SelectedAlgorithmProvider');
  }
  return context;
}

