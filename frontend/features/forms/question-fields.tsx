"use client";
import { Input } from "@/components/ui/input";
import type { Answer, Answers, FormField } from "./types";
import { visibleFields } from "./types";
export const formControl = "mt-2 min-h-12 w-full rounded-md border bg-background px-3 py-2 text-base disabled:opacity-70";
export function QuestionFields({ fields, answers, update, disabled, errors = {} }: { fields: FormField[]; answers: Answers; update: (key: string, value: Answer | undefined) => void; disabled?: boolean; errors?: Record<string, string> }) {
  return <div className="space-y-6">{visibleFields(fields, answers).map((field) => {
    const id = `answer-${field.key}`, help = `${id}-help`, error = `${id}-error`, value = Object.hasOwn(answers, field.key) ? answers[field.key] : undefined;
    const fieldError = Object.hasOwn(errors, field.key) ? errors[field.key] : undefined;
    const common = { id, "aria-describedby": `${help} ${error}`, "aria-invalid": Boolean(fieldError), disabled };
    const label = <>{field.label}{field.required ? <span className="ml-1 text-muted-foreground">(required)</span> : <span className="ml-1 text-sm font-normal text-muted-foreground">(optional)</span>}</>;
    let input;
    if (["RADIO", "CHECKBOX", "MULTI_SELECT"].includes(field.type)) {
      input = <fieldset {...common} className="min-w-0"><legend className="font-semibold">{label}</legend><div className="mt-2 grid gap-2 sm:grid-cols-2">{field.options.map((option) => <label key={option} className="flex min-h-12 cursor-pointer items-center gap-3 rounded-md border px-3 py-2"><input type={field.type === "RADIO" ? "radio" : "checkbox"} name={id} disabled={disabled} className="size-4 shrink-0 accent-primary" checked={field.type === "RADIO" ? value === option : Array.isArray(value) && value.includes(option)} onChange={(event) => update(field.key, field.type === "RADIO" ? option : event.target.checked ? [...(Array.isArray(value) ? value : []), option] : (Array.isArray(value) ? value : []).filter((v) => v !== option))} />{option}</label>)}</div></fieldset>;
    } else {
      const control = field.type === "TEXTAREA" ? <textarea {...common} className={`${formControl} min-h-32`} maxLength={field.maxLength ?? 2000} value={String(value ?? "")} onChange={(e) => update(field.key, e.target.value)} />
        : field.type === "BOOLEAN" || field.type === "DROPDOWN" ? <select {...common} className={formControl} value={value === undefined ? "" : String(value)} onChange={(e) => update(field.key, e.target.value === "" ? undefined : field.type === "BOOLEAN" ? e.target.value === "true" : e.target.value)}><option value="">Select an answer</option>{field.type === "BOOLEAN" ? <><option value="true">Yes</option><option value="false">No</option></> : field.options.map((option) => <option key={option}>{option}</option>)}</select>
          : <Input {...common} className="mt-2" type={{ NUMBER: "number", EMAIL: "email", URL: "url", DATE: "date" }[field.type as string] ?? "text"} step={field.type === "NUMBER" ? "any" : undefined} min={field.min} max={field.max} maxLength={field.maxLength ?? 2000} value={typeof value === "string" || typeof value === "number" ? value : ""} onChange={(e) => update(field.key, field.type === "NUMBER" ? e.target.value === "" ? undefined : Number(e.target.value) : e.target.value)} />;
      input = <><label htmlFor={id} className="font-semibold">{label}</label>{control}</>;
    }
    return <div key={field.key} className="min-w-0 [overflow-wrap:anywhere]">{input}<p id={help} className="mt-2 text-sm text-muted-foreground">{field.helpText}{field.type === "NUMBER" && (field.min !== undefined || field.max !== undefined) ? ` Allowed range: ${field.min ?? "no minimum"} to ${field.max ?? "no maximum"}.` : ""}</p><p id={error} className="mt-1 text-sm font-semibold text-danger">{fieldError}</p></div>;
  })}</div>;
}
