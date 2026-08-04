import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { AddReviewDialog } from './add-review-dialog';

describe('AddReviewDialog', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<AddReviewDialog>>;

  beforeEach(() => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<AddReviewDialog>>(['close']);
    TestBed.configureTestingModule({
      imports: [AddReviewDialog, ReactiveFormsModule],
      providers: [{ provide: MatDialogRef, useValue: dialogRef }],
    });
  });

  it('closes with the form value on submit', () => {
    const fixture = TestBed.createComponent(AddReviewDialog);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ rating: 4, comment: 'Pretty good' });
    fixture.componentInstance.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({ rating: 4, comment: 'Pretty good' });
  });

  it('closes with undefined on cancel', () => {
    const fixture = TestBed.createComponent(AddReviewDialog);
    fixture.detectChanges();

    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith(undefined);
  });
});
