export type ApiResponse<T> = {
  ok: boolean;
  data: T;
  message: string;
  errorCode: string | null;
};

export class ApiError extends Error {
  readonly errorCode: string | null;

  constructor(message: string, errorCode: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.errorCode = errorCode;
  }
}

