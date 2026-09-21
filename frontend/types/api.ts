export type ApiSuccess<T, M extends Record<string, unknown> = Record<string, unknown>> = {
  success: true;
  data: T;
  meta: M;
  requestId: string;
};

export type FieldError = {
  field: string;
  message: string;
};

export type ApiFailure = {
  success: false;
  error: {
    code: string;
    message: string;
    fieldErrors: FieldError[];
  };
  requestId: string;
};

export type ApiResponse<T, M extends Record<string, unknown> = Record<string, unknown>> =
  | ApiSuccess<T, M>
  | ApiFailure;

