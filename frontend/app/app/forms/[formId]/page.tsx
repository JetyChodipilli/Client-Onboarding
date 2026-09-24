import { FormBuilder } from "@/features/forms/form-builder";
export default async function Page({ params }: { params: Promise<{ formId: string }> }) { return <FormBuilder formId={(await params).formId} />; }
