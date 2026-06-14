export type AlgorithmVisualMeta = {
  imageSrc: string;
  imageAlt: string;
  overview: string;
  detail: string;
  strengths: string[];
  recommendedFor: string[];
};

const DEFAULT_VISUAL_META: AlgorithmVisualMeta = {
  imageSrc: '/images/algorithms/default.png',
  imageAlt: 'Algorithm overview image',
  overview: 'Review the selected demo algorithm and its intended use.',
  detail: 'Choose an algorithm based on the dataset shape, target workflow, and demo analysis goal.',
  strengths: ['Clear workflow fit', 'Easy comparison'],
  recommendedFor: ['Demo algorithm review'],
};

const ALGORITHM_VISUAL_META: Record<string, AlgorithmVisualMeta> = {
  ANN: {
    imageSrc: '/ANN.png',
    imageAlt: 'ANN algorithm image',
    overview: 'A neural network that learns patterns through connected layers.',
    detail: 'ANN models can capture non-linear relationships in structured feature data.',
    strengths: ['Non-linear pattern learning', 'Flexible feature handling'],
    recommendedFor: ['Complex pattern recognition', 'General classification'],
  },
  AUTOENCODER: {
    imageSrc: '/AutoEncoder.png',
    imageAlt: 'AutoEncoder algorithm image',
    overview: 'A reconstruction model that compresses input features and restores them.',
    detail: 'High reconstruction error can be used as a signal for unusual behavior.',
    strengths: ['Feature compression', 'Reconstruction-error scoring'],
    recommendedFor: ['Unsupervised anomaly detection', 'Dimensionality reduction'],
  },
  GRU: {
    imageSrc: '/GRU.png',
    imageAlt: 'GRU algorithm image',
    overview: 'A recurrent model designed for sequential data.',
    detail: 'GRU uses gating to retain useful sequence information with a lighter structure than LSTM.',
    strengths: ['Lightweight sequence learning', 'Gated memory'],
    recommendedFor: ['Time-series modeling', 'Sequence forecasting'],
  },
  ISOLATION_FOREST: {
    imageSrc: '/Isolation Forest.png',
    imageAlt: 'Isolation Forest algorithm image',
    overview: 'An anomaly detection algorithm that isolates unusual rows quickly.',
    detail: 'Outliers are separated with fewer splits than normal rows, producing anomaly scores.',
    strengths: ['Fast anomaly scoring', 'Good for tabular features'],
    recommendedFor: ['Unsupervised anomaly detection', 'Sensor feature monitoring'],
  },
  LIGHTGBM: {
    imageSrc: '/LightGBM.jpg',
    imageAlt: 'LightGBM algorithm image',
    overview: 'A gradient boosting model optimized for efficient tree-based learning.',
    detail: 'LightGBM is often useful when speed and strong tabular performance both matter.',
    strengths: ['Fast training', 'Strong tabular performance'],
    recommendedFor: ['Classification', 'Large feature tables'],
  },
  LIGHTGBM_REGRESSOR: {
    imageSrc: '/LightGBM Regressor.jpg',
    imageAlt: 'LightGBM Regressor algorithm image',
    overview: 'A LightGBM variant for continuous value prediction.',
    detail: 'It combines boosted trees to reduce prediction error in regression workflows.',
    strengths: ['Fast regression', 'Strong numeric prediction'],
    recommendedFor: ['Continuous value prediction', 'Large regression datasets'],
  },
  LINEAR_REGRESSION: {
    imageSrc: '/Linear Regression.jpg',
    imageAlt: 'Linear Regression algorithm image',
    overview: 'A baseline regression model for linear relationships.',
    detail: 'It is easy to interpret and useful as a simple benchmark.',
    strengths: ['Interpretable', 'Fast baseline'],
    recommendedFor: ['Baseline regression', 'Explainable prediction'],
  },
  LOGISTIC_REGRESSION: {
    imageSrc: '/Logistic Regression.jpg',
    imageAlt: 'Logistic Regression algorithm image',
    overview: 'A linear classifier that estimates class probability.',
    detail: 'It is useful when a transparent binary classification baseline is needed.',
    strengths: ['Interpretable', 'Fast inference'],
    recommendedFor: ['Binary classification', 'Explainable classification'],
  },
  LSTM: {
    imageSrc: '/LSTM.png',
    imageAlt: 'LSTM algorithm image',
    overview: 'A recurrent model that learns long-range sequence patterns.',
    detail: 'LSTM gates help retain useful context across longer time windows.',
    strengths: ['Long sequence memory', 'Time-series pattern learning'],
    recommendedFor: ['Time-series forecasting', 'Sequential analysis'],
  },
  LSTM_AE: {
    imageSrc: '/LSTM-AE.png',
    imageAlt: 'LSTM-AE algorithm image',
    overview: 'An LSTM autoencoder for reconstructing sequential windows.',
    detail: 'Sequence reconstruction error can indicate abnormal operating patterns.',
    strengths: ['Sequence anomaly detection', 'Temporal reconstruction'],
    recommendedFor: ['Equipment anomaly signals', 'Sequential anomaly detection'],
  },
  MLP: {
    imageSrc: '/MLP.jpg',
    imageAlt: 'MLP algorithm image',
    overview: 'A feed-forward neural network for general non-linear learning.',
    detail: 'MLP models can support classification and regression on engineered features.',
    strengths: ['Flexible non-linear learning', 'Broad applicability'],
    recommendedFor: ['General classification', 'General regression'],
  },
  ONE_CLASS_SVM: {
    imageSrc: '/One-Class SVM.png',
    imageAlt: 'One-Class SVM algorithm image',
    overview: 'A boundary-based model for detecting rows outside a normal region.',
    detail: 'It is useful when most training data represents normal behavior.',
    strengths: ['Normal-boundary learning', 'Unsupervised scoring'],
    recommendedFor: ['Normal-only training', 'Unsupervised anomaly detection'],
  },
  RANDOM_FOREST: {
    imageSrc: '/Random Forest.jpg',
    imageAlt: 'Random Forest algorithm image',
    overview: 'An ensemble of decision trees for stable classification.',
    detail: 'Multiple trees vote together to reduce overfitting and improve robustness.',
    strengths: ['Stable performance', 'Feature importance'],
    recommendedFor: ['Classification', 'Demo supervised workflows'],
  },
  RANDOM_FOREST_REGRESSOR: {
    imageSrc: '/Random Forest Regressor.jpg',
    imageAlt: 'Random Forest Regressor algorithm image',
    overview: 'An ensemble of regression trees for continuous prediction.',
    detail: 'It averages tree predictions to provide robust regression results.',
    strengths: ['Stable regression', 'Non-linear relationships'],
    recommendedFor: ['Continuous prediction', 'Baseline regression'],
  },
  SVM: {
    imageSrc: '/SVM.jpg',
    imageAlt: 'SVM algorithm image',
    overview: 'A margin-based classifier that separates classes with a decision boundary.',
    detail: 'SVM can be effective for smaller, high-dimensional datasets.',
    strengths: ['Clear boundary learning', 'High-dimensional support'],
    recommendedFor: ['Small classification datasets', 'Boundary-focused classification'],
  },
  TCN: {
    imageSrc: '/TCN.png',
    imageAlt: 'TCN algorithm image',
    overview: 'A convolutional model for sequential data.',
    detail: 'Dilated convolutions help capture longer temporal context efficiently.',
    strengths: ['Parallel sequence processing', 'Long context windows'],
    recommendedFor: ['Time-series forecasting', 'Fast sequence modeling'],
  },
  XGBOOST: {
    imageSrc: '/XGBoost.jpg',
    imageAlt: 'XGBoost algorithm image',
    overview: 'A boosted tree model focused on strong predictive performance.',
    detail: 'It learns trees sequentially to reduce prior errors and improve accuracy.',
    strengths: ['Strong accuracy', 'Regularized boosting'],
    recommendedFor: ['High-performance classification', 'Performance-focused demos'],
  },
  XGBOOST_REGRESSOR: {
    imageSrc: '/XGBoost Regressor.jpg',
    imageAlt: 'XGBoost Regressor algorithm image',
    overview: 'An XGBoost variant for continuous value prediction.',
    detail: 'It is useful for regression problems with complex tabular relationships.',
    strengths: ['Strong regression', 'Error-driven boosting'],
    recommendedFor: ['Performance-focused regression', 'Continuous value prediction'],
  },
  ARIMA: {
    imageSrc: '/ARIMA.png',
    imageAlt: 'ARIMA algorithm image',
    overview: 'A statistical time-series forecasting model.',
    detail: 'ARIMA combines autoregression, differencing, and moving averages for forecasting.',
    strengths: ['Interpretable forecasting', 'Classic time-series baseline'],
    recommendedFor: ['Baseline forecasting', 'Trend analysis'],
  },
  PROPHET: {
    imageSrc: '/Prophet.png',
    imageAlt: 'Prophet algorithm image',
    overview: 'A forecasting model that separates trend and seasonality components.',
    detail: 'Prophet is useful for quick, interpretable forecasting workflows.',
    strengths: ['Seasonality handling', 'Fast setup'],
    recommendedFor: ['Demand forecasting', 'Seasonal analysis'],
  },
  KMEANS: {
    imageSrc: '/K-Means.png',
    imageAlt: 'K-Means algorithm image',
    overview: 'A clustering algorithm that groups rows around centroids.',
    detail: 'It iteratively updates cluster centers to reduce within-cluster distance.',
    strengths: ['Simple clustering', 'Fast grouping'],
    recommendedFor: ['Cluster exploration', 'Pattern grouping'],
  },
  DBSCAN: {
    imageSrc: '/DBSCAN.png',
    imageAlt: 'DBSCAN algorithm image',
    overview: 'A density-based clustering algorithm.',
    detail: 'DBSCAN can identify dense groups and mark sparse points as noise.',
    strengths: ['Noise detection', 'Flexible cluster shapes'],
    recommendedFor: ['Density-based clustering', 'Data with outliers'],
  },
  HIERARCHICAL: {
    imageSrc: '/HierarchicalClustering.png',
    imageAlt: 'Hierarchical Clustering algorithm image',
    overview: 'A clustering method that builds nested group structures.',
    detail: 'It helps inspect relationships between clusters at different levels.',
    strengths: ['Hierarchy inspection', 'Interpretable clusters'],
    recommendedFor: ['Cluster relationship analysis', 'Visual clustering review'],
  },
};

export function getAlgorithmVisualMeta(algoCd?: string | null): AlgorithmVisualMeta {
  if (!algoCd) {
    return DEFAULT_VISUAL_META;
  }
  return ALGORITHM_VISUAL_META[algoCd] ?? DEFAULT_VISUAL_META;
}
