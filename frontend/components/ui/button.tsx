import { cva, type VariantProps } from "class-variance-authority";
import type * as React from "react";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-md px-4 text-sm font-semibold transition-[background-color,color,box-shadow,transform] duration-200 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-45 [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground shadow-sm hover:bg-primary/90 active:translate-y-px",
        accent:
          "bg-accent text-accent-foreground shadow-sm hover:bg-accent/88 active:translate-y-px",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-secondary/75 active:translate-y-px",
        outline:
          "border border-border bg-background/70 text-foreground hover:bg-muted active:translate-y-px",
        ghost: "text-foreground hover:bg-muted active:translate-y-px",
        danger:
          "bg-danger text-danger-foreground shadow-sm hover:bg-danger/90 active:translate-y-px",
      },
      size: {
        default: "h-11 px-5",
        sm: "h-10 min-h-10 px-4",
        lg: "h-12 min-h-12 px-6 text-base",
        icon: "size-11 min-h-11 px-0",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

function Button({ className, variant, size, type = "button", ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}

export { Button, buttonVariants };

