export type FieldError = {
  field: string;
  message: string;
};

export type ApiError = {
  code: string;
  message: string;
  fieldErrors: FieldError[];
};

export type PageMeta = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ApiSuccess<T, M = Record<string, unknown>> = {
  success: true;
  data: T;
  meta: M;
  requestId: string;
};

export type ApiFailure = {
  success: false;
  error: ApiError;
  requestId: string;
};

export type ApiResponse<T, M = Record<string, unknown>> = ApiSuccess<T, M> | ApiFailure;

export type ApiResult<T, M = Record<string, unknown>> = {
  data: T;
  meta: M;
  requestId: string;
};
