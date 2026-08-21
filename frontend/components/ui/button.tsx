import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex min-h-10 items-center justify-center gap-2 rounded-[var(--radius-md)] px-4 text-sm font-semibold transition-colors focus-visible:outline-none disabled:pointer-events-none disabled:opacity-55",
  {
    variants: {
      variant: {
        primary: "bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:bg-[hsl(var(--primary)/0.92)]",
        secondary: "border bg-white text-[hsl(var(--foreground))] hover:bg-[hsl(var(--surface-subtle))]",
        ghost: "text-[hsl(var(--foreground))] hover:bg-[hsl(var(--surface-subtle))]",
        destructive: "bg-[hsl(var(--danger))] text-white hover:bg-[hsl(var(--danger)/0.92)]",
      },
      size: {
        default: "h-10 px-4",
        sm: "h-9 rounded-lg px-3 text-xs",
        lg: "h-11 px-5",
        icon: "size-10 p-0",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />;
  },
);
Button.displayName = "Button";

export { buttonVariants };
