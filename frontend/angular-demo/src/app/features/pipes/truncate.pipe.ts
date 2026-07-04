import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'truncate', pure: true })
export class TruncatePipe implements PipeTransform {
  transform(value: string, maxLength = 20): string {
    if (value.length <= maxLength) {
      return value;
    }
    return `${value.slice(0, maxLength).trimEnd()}…`;
  }
}
