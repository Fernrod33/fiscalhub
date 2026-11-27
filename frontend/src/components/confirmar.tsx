import type { ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';

/** Confirmação de ação irreversível (ex.: excluir). */
export function Confirmar({ aberto, aoMudar, titulo, descricao, rotuloAcao, aoConfirmar, ocupado }: {
  aberto: boolean;
  aoMudar: (aberto: boolean) => void;
  titulo: string;
  descricao: ReactNode;
  rotuloAcao: string;
  aoConfirmar: () => void;
  ocupado?: boolean;
}) {
  return (
    <Dialog open={aberto} onOpenChange={aoMudar}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          <DialogDescription>{descricao}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose asChild><Button variant="outline">Cancelar</Button></DialogClose>
          <Button variant="destructive" onClick={aoConfirmar} disabled={ocupado}>{rotuloAcao}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
