import type { Control, FieldPath, FieldValues } from 'react-hook-form';
import { FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';

/** Campo de texto ligado ao react-hook-form (rótulo, mensagem de erro e descrição acessíveis). */
export function CampoTexto<T extends FieldValues>({ control, name, rotulo, type = 'text', autoComplete, placeholder, descricao }: {
  control: Control<T>;
  name: FieldPath<T>;
  rotulo: string;
  type?: string;
  autoComplete?: string;
  placeholder?: string;
  descricao?: string;
}) {
  return (
    <FormField control={control} name={name} render={({ field }) => (
      <FormItem>
        <FormLabel>{rotulo}</FormLabel>
        <FormControl>
          <Input type={type} autoComplete={autoComplete} placeholder={placeholder} {...field} value={field.value ?? ''} />
        </FormControl>
        {descricao && <FormDescription>{descricao}</FormDescription>}
        <FormMessage />
      </FormItem>
    )} />
  );
}
